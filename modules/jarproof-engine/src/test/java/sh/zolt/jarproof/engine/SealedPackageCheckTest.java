package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

final class SealedPackageCheckTest {
    private static final String SEALED_PACKAGE = "com/acme/sealed";

    @TempDir
    Path workspace;

    @Test
    void reportsAnArchiveWideSealAnotherArtifactIntrudesOn() {
        Path sealed = archiveWideSeal();
        Path intruder = jar("lib/intruder.jar", SEALED_PACKAGE + "/Intruder");

        Finding finding =
                EngineFixture.required(EngineFixture.verify(List.of(sealed), List.of(intruder), 17), "JP2008");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(SEALED_PACKAGE, finding.subject());
        assertEquals(sealed.toString(), finding.artifact().artifact());
        assertTrue(EngineFixture.evidence(finding).contains(sealed + " seals this package"),
                EngineFixture.evidence(finding).toString());
        assertTrue(EngineFixture.evidence(finding).contains(intruder + " adds classes to it anyway"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsASealDeclaredForOnePackageSection() {
        Manifest manifest = EngineFixture.manifest();
        Attributes section = new Attributes();
        section.put(Attributes.Name.SEALED, "true");
        manifest.getEntries().put(SEALED_PACKAGE + "/", section);
        Path sealed = EngineFixture.jar(workspace, "lib/sectioned.jar", EngineFixture.withManifest(
                EngineFixture.entries(SEALED_PACKAGE + "/Owner.class",
                        EngineFixture.classFile(SEALED_PACKAGE + "/Owner")), manifest));
        Path intruder = jar("lib/intruder.jar", SEALED_PACKAGE + "/Intruder");

        Finding finding =
                EngineFixture.required(EngineFixture.verify(List.of(sealed), List.of(intruder), 17), "JP2008");

        assertEquals(SEALED_PACKAGE, finding.subject());
    }

    @Test
    void staysQuietWhenNothingIntrudesOnTheSeal() {
        Path sealed = archiveWideSeal();

        List<Finding> findings = EngineFixture.verify(List.of(sealed), List.of(), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2008"));
    }

    @Test
    void ignoresASealDeclaredForSomethingThatIsNotAPackage() {
        Manifest manifest = EngineFixture.manifest();
        Attributes section = new Attributes();
        section.put(Attributes.Name.SEALED, "true");
        manifest.getEntries().put(SEALED_PACKAGE + "/Owner.class", section);
        Path sealed = EngineFixture.jar(workspace, "lib/entry-sealed.jar", EngineFixture.withManifest(
                EngineFixture.entries(SEALED_PACKAGE + "/Owner.class",
                        EngineFixture.classFile(SEALED_PACKAGE + "/Owner")), manifest));
        Path intruder = jar("lib/intruder.jar", SEALED_PACKAGE + "/Intruder");

        List<Finding> findings = EngineFixture.verify(List.of(sealed), List.of(intruder), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2008"));
    }

    private Path archiveWideSeal() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.SEALED, "TRUE");
        return EngineFixture.jar(workspace, "lib/sealed.jar", EngineFixture.withManifest(
                EngineFixture.entries(SEALED_PACKAGE + "/Owner.class",
                        EngineFixture.classFile(SEALED_PACKAGE + "/Owner")), manifest));
    }

    private Path jar(String name, String internalName) {
        return EngineFixture.jar(workspace, name,
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName)));
    }
}
