package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class ModuleDescriptorCheckTest {
    private static final String ARCHIVE = "lib/orders.jar";
    private static final String OTHER_ARCHIVE = "lib/legacy.jar";
    private static final String EXPOSED = "com/acme/orders/spi";
    private static final String OTHER_MODULE = "com.acme.legacy";
    private static final String OTHER_PACKAGE = "com/acme/legacy";

    @TempDir
    Path workspace;

    @Test
    void reportsARequirementNothingSupplies() {
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, 0, null)));

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5001");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(ModuleFixture.ABSENT_MODULE, finding.subject());
        assertEquals(archive.toString(), finding.artifact().artifact());
        assertEquals(ModuleFixture.DESCRIPTOR_ENTRY, finding.artifact().classEntry().orElseThrow());
        assertEquals("required module resolves nowhere", finding.summary());
        assertTrue(EngineFixture.evidence(finding).contains(
                        "the descriptor of module " + ModuleFixture.MODULE + " requires that module"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void exemptsACompileOnlyRequirement() {
        Path archive = modular(ModuleFixture.descriptor(ModuleFixture.MODULE,
                module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, Opcodes.ACC_STATIC_PHASE, null)));

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void reportsATransitiveRequirementNothingSupplies() {
        Path archive = modular(ModuleFixture.descriptor(ModuleFixture.MODULE,
                module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, Opcodes.ACC_TRANSITIVE, null)));

        assertEquals(List.of("JP5001"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void staysQuietAboutTheImplicitBaseModule() {
        Path archive = modular(ModuleFixture.bareDescriptor(ModuleFixture.MODULE));

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void resolvesARequirementAgainstAPlatformModule() {
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(ModuleFixture.PLATFORM_MODULE, 0, null)));

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void resolvesARequirementAgainstAnotherDescriptor() {
        Path other = EngineFixture.jar(workspace, OTHER_ARCHIVE,
                ModuleFixture.withDescriptor(EngineFixture.entries(), ModuleFixture.bareDescriptor(OTHER_MODULE)));
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(OTHER_MODULE, 0, null)));

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void resolvesARequirementAgainstAReservedModuleName() {
        Path other = EngineFixture.jar(workspace, OTHER_ARCHIVE, EngineFixture.withManifest(
                EngineFixture.entries(OTHER_PACKAGE + "/Legacy.class", EngineFixture.classFile(OTHER_PACKAGE + "/Legacy")),
                ModuleFixture.reserving(OTHER_MODULE)));
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(OTHER_MODULE, 0, null)));

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void reportsAnExposedPackageWithNoClasses() {
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitExport(EXPOSED, 0)));

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5002");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(EXPOSED, finding.subject());
        assertEquals("exposed package holds no classes here", finding.summary());
        assertEquals(
                List.of("module " + ModuleFixture.MODULE + " exports or opens that package",
                        "this artifact declares no class in it"),
                EngineFixture.evidence(finding));
    }

    @Test
    void reportsAQualifiedExportOfAPackageWithNoClasses() {
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitExport(EXPOSED, 0, OTHER_MODULE)));

        assertEquals(List.of("JP5002"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void staysQuietAboutAnExposedPackageThatHasClasses() {
        Path archive = EngineFixture.jar(workspace, ARCHIVE, ModuleFixture.withDescriptor(
                EngineFixture.entries(
                        ModuleFixture.PACKAGE + "/Order.class", EngineFixture.classFile(ModuleFixture.PACKAGE + "/Order")),
                ModuleFixture.descriptor(ModuleFixture.MODULE, module -> {
                    module.visitExport(ModuleFixture.PACKAGE, 0);
                    module.visitOpen(ModuleFixture.PACKAGE, 0);
                })));

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void collapsesTheExportAndTheOpenOfOneMissingPackage() {
        Path archive = modular(ModuleFixture.descriptor(ModuleFixture.MODULE, module -> {
            module.visitExport(EXPOSED, 0);
            module.visitOpen(EXPOSED, 0);
        }));

        List<Finding> findings = ModuleFixture.findings(archive);

        assertEquals(List.of("JP5002"), EngineFixture.codes(findings));
        assertEquals(EXPOSED, findings.get(0).subject());
    }

    private Path modular(byte[] descriptor) {
        Map<String, byte[]> entries = ModuleFixture.withDescriptor(EngineFixture.entries(), descriptor);
        return EngineFixture.jar(workspace, ARCHIVE, entries);
    }
}
