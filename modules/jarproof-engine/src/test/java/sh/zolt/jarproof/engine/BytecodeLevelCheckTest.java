package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

final class BytecodeLevelCheckTest {
    @TempDir
    Path workspace;

    @Test
    void reportsAnArchiveThatMixesLevels() {
        Map<String, byte[]> entries = EngineFixture.entries(
                "com/acme/mixed/Old.class", EngineFixture.classFile("com/acme/mixed/Old", Opcodes.V1_8));
        entries.put("com/acme/mixed/New.class", EngineFixture.classFile("com/acme/mixed/New"));
        entries.put("com/acme/mixed/Newer.class", EngineFixture.classFile("com/acme/mixed/Newer"));
        Path archive = EngineFixture.jar(workspace, "lib/mixed.jar", entries);

        Finding finding = EngineFixture.required(EngineFixture.verify(List.of(archive), List.of(), 17), "JP3005");

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(archive.toString(), finding.subject());
        assertEquals(Optional.empty(), finding.artifact().classEntry());
        assertEquals(
                List.of("class file version 52 appears 1 times", "class file version 61 appears 2 times"),
                EngineFixture.evidence(finding));
        assertEquals(
                List.of(
                        "No action is required when every reported class file version is supported by the"
                                + " target Java runtime. If the mix was accidental, rebuild from clean output;"
                                + " use a multi-release JAR only for deliberate release-specific variants."),
                finding.remediation().stream().map(Remediation::action).toList());
    }

    @Test
    void staysQuietWhenOneLevelCoversTheArchive() {
        Map<String, byte[]> entries = EngineFixture.entries(
                "com/acme/single/One.class", EngineFixture.classFile("com/acme/single/One"));
        entries.put("com/acme/single/Two.class", EngineFixture.classFile("com/acme/single/Two"));
        Path archive = EngineFixture.jar(workspace, "lib/single.jar", entries);

        assertEquals(List.of(), EngineFixture.verify(List.of(archive), List.of(), 17));
    }

    @Test
    void countsOnlyTheEntriesTheTargetRuntimeWouldSelect() {
        Map<String, byte[]> entries = EngineFixture.entries(
                "com/acme/picked/One.class", EngineFixture.classFile("com/acme/picked/One", Opcodes.V1_8));
        entries.put("META-INF/versions/17/com/acme/picked/One.class",
                EngineFixture.classFile("com/acme/picked/One"));
        java.util.jar.Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MULTI_RELEASE, "true");
        Path archive =
                EngineFixture.jar(workspace, "lib/picked.jar", EngineFixture.withManifest(entries, manifest));

        List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);

        assertEquals(List.of(), findings);
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP3005"));
    }
}
