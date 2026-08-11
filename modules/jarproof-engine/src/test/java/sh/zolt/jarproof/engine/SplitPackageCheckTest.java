package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

final class SplitPackageCheckTest {
    @TempDir
    Path workspace;

    @Test
    void reportsAPackageTwoArtifactsContributeTo() {
        Path first = jar("lib/first.jar", "com/acme/split/First");
        Path second = jar("lib/second.jar", "com/acme/split/Second");

        Finding finding =
                EngineFixture.required(EngineFixture.verify(List.of(first), List.of(second), 17), "JP2003");

        assertEquals(Severity.INFO, finding.severity());
        assertEquals("com/acme/split", finding.subject());
        assertEquals(first.toString(), finding.artifact().artifact());
        assertEquals(2, finding.evidence().size());
        assertTrue(EngineFixture.evidence(finding).contains(second + " contributes classes to this package"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void staysQuietAboutTheUnnamedPackage() {
        Path first = jar("lib/first.jar", "Loose");
        Path second = jar("lib/second.jar", "Looser");

        List<Finding> findings = EngineFixture.verify(List.of(first), List.of(second), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2003"));
    }

    /**
     * An entry name that opens with a separator names a class in the unnamed package as well. A leading
     * separator is no package, so two artifacts holding such an entry share nothing; reporting them would
     * name a split package with an empty subject.
     */
    @Test
    void staysQuietAboutAnEntryNameThatOpensWithASeparator() {
        Path first = jar("lib/rooted-first.jar", "/Loose");
        Path second = jar("lib/rooted-second.jar", "/Looser");

        List<Finding> findings = EngineFixture.verify(List.of(first), List.of(second), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2003"));
    }

    @Test
    void staysQuietWhenOneArtifactOwnsThePackage() {
        Path owner = EngineFixture.jar(workspace, "lib/owner.jar", ownedPackage());

        assertEquals(List.of(), EngineFixture.verify(List.of(owner), List.of(), 17));
    }

    private java.util.Map<String, byte[]> ownedPackage() {
        java.util.Map<String, byte[]> entries = EngineFixture.entries();
        entries.put("com/acme/owned/First.class", EngineFixture.classFile("com/acme/owned/First"));
        entries.put("com/acme/owned/Second.class", EngineFixture.classFile("com/acme/owned/Second"));
        return entries;
    }

    private Path jar(String name, String internalName) {
        return EngineFixture.jar(workspace, name,
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName)));
    }
}
