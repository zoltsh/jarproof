package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class InspectCommandTest {
    private static final String INSPECT = "inspect";
    private static final String CODEC = "com.acme.spi.Codec";
    private static final String MODERN = "com/acme/app/Modern";
    private static final int JAVA_8_MAJOR = 52;
    private static final int JAVA_17_MAJOR = 61;
    private static final int JAVA_21_MAJOR = 65;

    @TempDir
    Path workspace;

    @Test
    void showsEveryFactAsAnAlignedTable() {
        Invocation invocation = CliFixture.invoke(INSPECT, multiReleaseArchive().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(
                """
                artifact:       app.jar
                entries:        5
                classes:        3
                bytecode:       52:1, 61:1, 65:1
                services:       com.acme.spi.Codec
                multi-release:  11, 21
                """,
                CliFixture.rooted(invocation.out(), workspace));
        assertEquals("", invocation.err());
    }

    @Test
    void showsTheSameFactsAsJson() {
        Invocation invocation = CliFixture.invoke(
                INSPECT, multiReleaseArchive().toString(), CliFixture.FORMAT, CliFixture.JSON);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(
                """
                {
                  "artifact": "app.jar",
                  "entryCount": 5,
                  "classCount": 3,
                  "bytecodeLevels": [
                    "52:1",
                    "61:1",
                    "65:1"
                  ],
                  "declaredServices": [
                    "com.acme.spi.Codec"
                  ],
                  "multiReleaseVersions": [
                    11,
                    21
                  ]
                }
                """,
                CliFixture.rooted(invocation.out(), workspace));
    }

    @Test
    void saysSoWhenAnArtifactHasNothingToReport() {
        Path bare = CliFixture.jar(workspace, "bare.jar", Map.of());

        Invocation invocation = CliFixture.invoke(INSPECT, bare.toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(
                """
                artifact:       bare.jar
                entries:        0
                classes:        0
                bytecode:       none
                services:       none
                multi-release:  none
                """,
                CliFixture.rooted(invocation.out(), workspace));
    }

    @Test
    void writesTheFactsWhereItWasAsked() throws Exception {
        Path facts = workspace.resolve("facts.json");

        Invocation invocation = CliFixture.invoke(
                INSPECT,
                multiReleaseArchive().toString(),
                CliFixture.FORMAT,
                CliFixture.JSON,
                "--output",
                facts.toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("", invocation.out());
        assertTrue(Files.readString(facts).contains("\"classCount\": 3"), facts.toString());
    }

    @Test
    void refusesAnArtifactItCannotRead() {
        Path absent = workspace.resolve("absent.jar");

        Invocation invocation = CliFixture.invoke(INSPECT, absent.toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains(absent.toString()), invocation.err());
        assertEquals("", invocation.out());
    }

    @Test
    void refusesAnOutputPathItCannotWrite() {
        Invocation invocation = CliFixture.invoke(
                INSPECT,
                multiReleaseArchive().toString(),
                "--output",
                workspace.resolve("absent").resolve("facts.txt").toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("absent"), invocation.err());
    }

    @Test
    void refusesAFormatItDoesNotHave() {
        Invocation invocation = CliFixture.invoke(
                INSPECT, multiReleaseArchive().toString(), CliFixture.FORMAT, "sarif");

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("sarif is not one of: human|json"), invocation.err());
    }

    @Test
    void refusesAnInspectionWithNothingToInspect() {
        Invocation invocation = CliFixture.invoke(INSPECT);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("ARTIFACT"), invocation.err());
    }

    /** One archive holding every fact inspect reports: versions, levels, services, and plain files. */
    private Path multiReleaseArchive() {
        Map<String, byte[]> entries = CliFixture.entries(
                MODERN + CliFixture.CLASS_SUFFIX, CliFixture.classFile(MODERN, JAVA_8_MAJOR));
        entries.put(
                "META-INF/versions/11/" + MODERN + CliFixture.CLASS_SUFFIX,
                CliFixture.classFile(MODERN, JAVA_17_MAJOR));
        entries.put(
                "META-INF/versions/21/" + MODERN + CliFixture.CLASS_SUFFIX,
                CliFixture.classFile(MODERN, JAVA_21_MAJOR));
        entries.put("META-INF/services/" + CODEC, "com.acme.spi.PlainCodec\n".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/NOTICE", "notice".getBytes(StandardCharsets.UTF_8));
        return CliFixture.jar(workspace, "app.jar", entries);
    }
}
