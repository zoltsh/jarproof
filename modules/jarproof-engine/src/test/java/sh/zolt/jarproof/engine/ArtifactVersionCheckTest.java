package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

final class ArtifactVersionCheckTest {
    @TempDir
    Path workspace;

    @Test
    void reportsOnePublishedCoordinateAtTwoVersions() {
        Path older = published("lib/widgets-1.0.0.jar", "com/acme/widgets/One", "com.acme", "widgets", "1.0.0");
        Path newer = published("lib/widgets-2.0.0.jar", "com/acme/other/Two", "com.acme", "widgets", "2.0.0");

        Finding finding =
                EngineFixture.required(EngineFixture.verify(List.of(older), List.of(newer), 17), "JP2004");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals("com.acme:widgets", finding.subject());
        assertEquals(older.toString(), finding.artifact().artifact());
        assertTrue(EngineFixture.evidence(finding).contains("2.0.0 is claimed by " + newer),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void fallsBackToTheIdentityTheManifestClaims() {
        Path older = titled("lib/older.jar", "com/acme/titled/One", "widgets", "1.0.0");
        Path newer = titled("lib/newer.jar", "com/acme/other/Two", "widgets", "3.0.0");

        Finding finding =
                EngineFixture.required(EngineFixture.verify(List.of(older), List.of(newer), 17), "JP2004");

        assertEquals(":widgets", finding.subject());
    }

    @Test
    void staysQuietAboutARepackagedArchiveThatClaimsSeveralIdentities() {
        Map<String, byte[]> entries =
                EngineFixture.entries("com/acme/shaded/One.class", EngineFixture.classFile("com/acme/shaded/One"));
        entries.put("META-INF/maven/com.acme/widgets/pom.properties", properties("com.acme", "widgets", "1.0.0"));
        entries.put("META-INF/maven/com.acme/gears/pom.properties", properties("com.acme", "gears", "9.0.0"));
        Path shaded = EngineFixture.jar(workspace, "lib/shaded.jar", entries);
        Path other = published("lib/widgets.jar", "com/acme/other/Two", "com.acme", "widgets", "2.0.0");

        List<Finding> findings = EngineFixture.verify(List.of(shaded), List.of(other), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2004"));
    }

    @Test
    void staysQuietWhenBothCopiesClaimTheSameVersion() {
        Path first = published("lib/first.jar", "com/acme/same/One", "com.acme", "widgets", "1.0.0");
        Path second = published("lib/second.jar", "com/acme/other/Two", "com.acme", "widgets", "1.0.0");

        List<Finding> findings = EngineFixture.verify(List.of(first), List.of(second), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2004"));
    }

    @Test
    void staysQuietWhenTheCoordinatePropertiesAreIncomplete() {
        Map<String, byte[]> entries =
                EngineFixture.entries("com/acme/partial/One.class", EngineFixture.classFile("com/acme/partial/One"));
        entries.put("META-INF/maven/com.acme/widgets/pom.properties",
                "groupId=com.acme\n".getBytes(StandardCharsets.UTF_8));
        Path partial = EngineFixture.jar(workspace, "lib/partial.jar", entries);

        assertEquals(List.of(), EngineFixture.verify(List.of(partial), List.of(), 17));
    }

    @Test
    void staysQuietWhenTheCoordinatePropertiesCannotBeParsed() {
        Map<String, byte[]> entries = EngineFixture.entries(
                "com/acme/mangled/One.class", EngineFixture.classFile("com/acme/mangled/One"));
        entries.put("META-INF/maven/com.acme/widgets/pom.properties",
                "artifactId=\\uZZZZ\n".getBytes(StandardCharsets.UTF_8));
        Path mangled = EngineFixture.jar(workspace, "lib/mangled.jar", entries);

        assertEquals(List.of(), EngineFixture.verify(List.of(mangled), List.of(), 17));
    }

    private Path published(String name, String internalName, String group, String artifact, String version) {
        Map<String, byte[]> entries =
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName));
        entries.put("META-INF/maven/" + group + "/" + artifact + "/pom.properties",
                properties(group, artifact, version));
        return EngineFixture.jar(workspace, name, entries);
    }

    private Path titled(String name, String internalName, String title, String version) {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, title);
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        return EngineFixture.jar(workspace, name, EngineFixture.withManifest(
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName)), manifest));
    }

    private static byte[] properties(String group, String artifact, String version) {
        return ("groupId=" + group + "\nartifactId=" + artifact + "\nversion=" + version + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
