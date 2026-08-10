package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class InspectCommandTest {
    private static final String INSPECT = "inspect";
    private static final String PATH_ROOT = "--path-root";
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
                artifact:         app.jar
                entries:          5
                classes:          3
                nested-archives:  0
                bytecode:         52:1, 61:1, 65:1
                services:         com.acme.spi.Codec
                multi-release:    11, 21
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
                  "inspectJsonVersion": "1",
                  "artifact": "app.jar",
                  "entryCount": 5,
                  "classCount": 3,
                  "nestedArchiveCount": 0,
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

    /**
     * The envelope is a contract, so the member set and the member order are pinned here as well as
     * inside the golden document -- the way the {@code check} envelope is pinned -- rather than left to
     * whatever the renderer happens to write next. {@code inspectJsonVersion} comes first and stays
     * first; an optional member may only ever join the end of this list, and renaming or dropping one
     * a consumer already reads is a version bump.
     *
     * <p>Which spelling of a path the {@code artifact} member carries is not part of that promise and
     * never was: the contract says the member names the artifact the caller named, and the caller
     * decides how. Measuring it from {@code --path-root} therefore changes the spelling inside a
     * version rather than the version, exactly as it does for the {@code check} envelope.
     */
    @Test
    void pinsTheMemberSetAndOrderOfTheVersionedEnvelope() {
        Invocation invocation = CliFixture.invoke(
                INSPECT, multiReleaseArchive().toString(), CliFixture.FORMAT, CliFixture.JSON);

        Map<?, ?> document = (Map<?, ?>) JsonScanner.parse(invocation.out());
        assertEquals(
                List.of(
                        "inspectJsonVersion",
                        "artifact",
                        "entryCount",
                        "classCount",
                        "nestedArchiveCount",
                        "bytecodeLevels",
                        "declaredServices",
                        "multiReleaseVersions"),
                List.copyOf(document.keySet()),
                invocation.out());
        assertEquals("1", document.get("inspectJsonVersion"), invocation.out());
    }

    @Test
    void countsTheArchivesAnApplicationCarries() {
        Map<String, byte[]> entries = CliFixture.entries(
                "BOOT-INF/classes/" + MODERN + CliFixture.CLASS_SUFFIX,
                CliFixture.classFile(MODERN, JAVA_17_MAJOR));
        entries.put("BOOT-INF/lib/api.jar", new byte[0]);

        Invocation invocation = CliFixture.invoke(INSPECT, CliFixture.jar(workspace, "fat.jar", entries).toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(
                """
                artifact:         fat.jar
                entries:          2
                classes:          1
                nested-archives:  1
                bytecode:         61:1
                services:         none
                multi-release:    none
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
                artifact:         bare.jar
                entries:          0
                classes:          0
                nested-archives:  0
                bytecode:         none
                services:         none
                multi-release:    none
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
    void refusesAnOutputPathWhoseDirectoryIsNotThere() {
        Path facts = workspace.resolve("absent").resolve("facts.txt");

        Invocation invocation = CliFixture.invoke(
                INSPECT, multiReleaseArchive().toString(), "--output", facts.toString());

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Cannot write the report to " + facts + ": the directory does not exist\n", invocation.err());
    }

    @Test
    void refusesAnOutputPathItIsNotAllowedToWrite() throws Exception {
        Path facts = Files.writeString(workspace.resolve("locked.txt"), "\n");
        Files.setPosixFilePermissions(facts, Set.of(PosixFilePermission.OWNER_READ));

        Invocation invocation = CliFixture.invoke(
                INSPECT, multiReleaseArchive().toString(), "--output", facts.toString());

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Cannot write the report to " + facts + ": permission was denied\n", invocation.err());
    }

    /**
     * The {@code artifact} member is measured from {@code --path-root} exactly as the {@code check}
     * envelope's is. That is not a change to the contract: the contract has always said this member
     * carries the path the caller named, and which spelling of it is the caller's own decision. The
     * member set and its order are what a consumer may rely on, and both are pinned above.
     */
    @Test
    void measuresTheArtifactFromThePathRootForTheVersionedEnvelope() {
        Path archive = multiReleaseArchive();

        Invocation invocation = CliFixture.invoke(
                INSPECT, archive.toString(), CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("\"artifact\": \"app.jar\""), invocation.out());
    }

    /** A person reads the path they typed, whatever root the machine form measures from. */
    @Test
    void keepsTheTableNamingTheArtifactAsItWasGiven() {
        Path archive = multiReleaseArchive();

        Invocation invocation =
                CliFixture.invoke(INSPECT, archive.toString(), PATH_ROOT, workspace.toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("artifact:         " + archive), invocation.out());
    }

    @Test
    void refusesAPathRootThatIsNotADirectory() {
        Path absent = workspace.resolve("nowhere");

        Invocation invocation = CliFixture.invoke(
                INSPECT, multiReleaseArchive().toString(), PATH_ROOT, absent.toString());

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "This path root is not a directory that exists: " + absent + "\n", invocation.err());
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
