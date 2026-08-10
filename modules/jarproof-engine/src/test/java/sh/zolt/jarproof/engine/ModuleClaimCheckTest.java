package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class ModuleClaimCheckTest {
    private static final String ARCHIVE = "lib/orders.jar";
    private static final String OTHER_ARCHIVE = "lib/legacy.jar";
    private static final String CLASSES = "classes";
    private static final String ORDER = ModuleFixture.PACKAGE + "/Order";
    private static final String LEGACY = ModuleFixture.PACKAGE + "/Legacy";
    private static final String ABSENT_SERVICE = "com/acme/absent/Codec";
    private static final String LEGAL_NAME = "com.acme.legacy";
    private static final String ILLEGAL_NAME = "com.acme..legacy";
    private static final String VERSIONED_DESCRIPTOR =
            ArchiveLayout.VERSIONS_PREFIX + "17/" + ModuleFixture.DESCRIPTOR_ENTRY;

    @TempDir
    Path workspace;

    @Test
    void reportsAConsumedServiceTypeNothingDeclares() {
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitUse(ABSENT_SERVICE)));

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5004");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(ABSENT_SERVICE, finding.subject());
        assertEquals("consumed service type resolves nowhere", finding.summary());
        assertTrue(EngineFixture.evidence(finding).contains(
                        "neither the target platform nor any supplied artifact declares that type"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void resolvesAConsumedServiceAgainstThePlatform() {
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitUse(ModuleFixture.PLATFORM_SERVICE)));

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void resolvesAConsumedServiceDeclaredByAnotherArtifact() {
        Path other = plain(OTHER_ARCHIVE, EngineFixture.entries(
                ABSENT_SERVICE + ArchiveLayout.CLASS_SUFFIX, ServiceFixture.serviceInterface(ABSENT_SERVICE)));
        Path archive = modular(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitUse(ABSENT_SERVICE)));

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void reportsAReservedNameThatIsNotAModuleName() {
        Path archive = reserved(ARCHIVE, ILLEGAL_NAME, ORDER);

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5006");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(ILLEGAL_NAME, finding.subject());
        assertEquals(archive.toString(), finding.artifact().artifact());
        assertEquals(java.util.Optional.empty(), finding.artifact().classEntry());
        assertEquals("reserved automatic module name is not a legal module name", finding.summary());
        assertTrue(EngineFixture.evidence(finding).contains("at least one segment of it is not a Java identifier"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void acceptsALegalReservedName() {
        assertEquals(List.of(), ModuleFixture.findings(reserved(ARCHIVE, LEGAL_NAME, ORDER)));
    }

    @Test
    void staysQuietAboutAnArtifactThatMakesNoModuleClaim() {
        Path archive = EngineFixture.jar(workspace, ARCHIVE, EngineFixture.withManifest(
                EngineFixture.entries(ORDER + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(ORDER)),
                EngineFixture.manifest()));

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void staysQuietAboutAnArchiveWithNoManifestAtAll() {
        assertEquals(List.of(), ModuleFixture.findings(plain(ARCHIVE, EngineFixture.entries(
                ORDER + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(ORDER)))));
    }

    @Test
    void reportsAPackageTwoModuleCapableArtifactsShare() {
        Path other = reserved(OTHER_ARCHIVE, LEGAL_NAME, LEGACY);
        Path archive = withClass(ModuleFixture.bareDescriptor(ModuleFixture.MODULE));

        Finding finding = EngineFixture.required(
                ModuleFixture.findings(List.of(archive), List.of(other)), "JP5005");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(ModuleFixture.PACKAGE, finding.subject());
        assertEquals(archive.toString(), finding.artifact().artifact());
        assertEquals("package split across module-capable artifacts", finding.summary());
        assertEquals(
                List.of(archive + " contributes classes to it as module " + ModuleFixture.MODULE,
                        other + " contributes classes to it as module " + LEGAL_NAME),
                EngineFixture.evidence(finding));
    }

    @Test
    void staysQuietAboutASplitPackageWhenOneSideMakesNoModuleClaim() {
        Path other = plain(OTHER_ARCHIVE, EngineFixture.entries(
                LEGACY + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(LEGACY)));
        Path archive = withClass(ModuleFixture.bareDescriptor(ModuleFixture.MODULE));

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void makesNoClaimForAClassFileThatOnlyLooksLikeADescriptor() {
        Path other = reserved(OTHER_ARCHIVE, LEGAL_NAME, LEGACY);
        Path archive = withClass(ModuleFixture.impostorDescriptor());

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void makesNoClaimForADescriptorNoParserAccepts() {
        Path other = reserved(OTHER_ARCHIVE, LEGAL_NAME, LEGACY);
        Path archive = withClass(ModuleFixture.futureDescriptor(ModuleFixture.MODULE));

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void readsTheDescriptorAMultiReleaseArchiveSelects() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, Boolean.TRUE.toString());
        Map<String, byte[]> entries = ModuleFixture.withDescriptor(
                EngineFixture.entries(), ModuleFixture.bareDescriptor(ModuleFixture.MODULE));
        entries.put(VERSIONED_DESCRIPTOR, ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, 0, null)));
        Path archive = EngineFixture.jar(workspace, ARCHIVE, EngineFixture.withManifest(entries, manifest));

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5001");

        assertEquals(VERSIONED_DESCRIPTOR, finding.artifact().classEntry().orElseThrow());
    }

    @Test
    void readsTheDescriptorOfAClassDirectory() {
        Path directory = EngineFixture.classDirectory(workspace, CLASSES, ModuleFixture.withDescriptor(
                EngineFixture.entries(ORDER + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(ORDER)),
                ModuleFixture.descriptor(
                        ModuleFixture.MODULE, module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, 0, null))));

        Finding finding = EngineFixture.required(ModuleFixture.findings(directory), "JP5001");

        assertEquals(directory.toString(), finding.artifact().artifact());
    }

    @Test
    void ignoresAManifestInsideAClassDirectory() {
        Map<String, byte[]> entries = EngineFixture.entries(
                ORDER + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(ORDER));
        entries.put(JarFile.MANIFEST_NAME, EngineFixture.manifestBytes(ModuleFixture.reserving(ILLEGAL_NAME)));
        Path directory = EngineFixture.classDirectory(workspace, CLASSES, entries);

        assertEquals(List.of(), ModuleFixture.findings(directory));
    }

    @Test
    void failsLoudlyWhenAnArchiveDisappearsAfterTheClasspathIsRead() throws IOException {
        Path archive = withClass(ModuleFixture.bareDescriptor(ModuleFixture.MODULE));
        ArtifactCatalog catalog = ModuleFixture.catalog(List.of(archive), List.of());
        Files.delete(archive);

        assertThrows(UncheckedIOException.class, () -> ModuleFixture.findings(catalog));
    }

    @Test
    void failsLoudlyWhenADirectoryDescriptorDisappearsAfterTheClasspathIsRead() throws IOException {
        Path directory = EngineFixture.classDirectory(workspace, CLASSES, ModuleFixture.withDescriptor(
                EngineFixture.entries(), ModuleFixture.bareDescriptor(ModuleFixture.MODULE)));
        ArtifactCatalog catalog = ModuleFixture.catalog(List.of(directory), List.of());
        Files.delete(directory.resolve(ModuleFixture.DESCRIPTOR_ENTRY));

        assertThrows(UncheckedIOException.class, () -> ModuleFixture.findings(catalog));
    }

    @Test
    void producesTheSameFindingsOnEveryRun() {
        Path other = reserved(OTHER_ARCHIVE, ILLEGAL_NAME, LEGACY);
        Path archive = withClass(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, 0, null)));

        assertEquals(
                ModuleFixture.findings(List.of(archive), List.of(other)),
                ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    @Test
    void reportsTheSameFaultsWhateverOrderTheClasspathArrivesIn() {
        Path other = reserved(OTHER_ARCHIVE, ILLEGAL_NAME, LEGACY);
        Path archive = withClass(ModuleFixture.descriptor(
                ModuleFixture.MODULE, module -> module.visitRequire(ModuleFixture.ABSENT_MODULE, 0, null)));

        List<String> forward = faults(ModuleFixture.findings(List.of(archive), List.of(other)));
        List<String> reversed = faults(ModuleFixture.findings(List.of(other), List.of(archive)));

        assertEquals(List.of("JP5001 " + ModuleFixture.ABSENT_MODULE, "JP5005 " + ModuleFixture.PACKAGE,
                "JP5006 " + ILLEGAL_NAME), forward);
        assertEquals(forward, reversed);
    }

    private static List<String> faults(List<Finding> findings) {
        List<Finding> ordered = new ArrayList<>(findings);
        ordered.sort(FindingOrder.CANONICAL);
        return ordered.stream().map(finding -> finding.code().value() + " " + finding.subject()).sorted().toList();
    }

    private Path modular(byte[] descriptor) {
        return EngineFixture.jar(
                workspace, ARCHIVE, ModuleFixture.withDescriptor(EngineFixture.entries(), descriptor));
    }

    private Path withClass(byte[] descriptor) {
        return EngineFixture.jar(workspace, ARCHIVE, ModuleFixture.withDescriptor(
                EngineFixture.entries(ORDER + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(ORDER)),
                descriptor));
    }

    private Path plain(String name, Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, name, entries);
    }

    private Path reserved(String name, String moduleName, String declared) {
        return EngineFixture.jar(workspace, name, EngineFixture.withManifest(
                EngineFixture.entries(declared + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(declared)),
                ModuleFixture.reserving(moduleName)));
    }
}
