package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class CheckCommandTest {
    private static final String PATH_ROOT = "--path-root";
    private static final String OUTPUT = "--output";
    private static final String SOURCE_ROOT = "--source-root";
    private static final String REPORTED_ARTIFACT = "app.jar";

    @TempDir
    Path workspace;

    @Test
    void reportsABrokenClasspathAsTheDesignSpecimen() {
        Invocation invocation = check();

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertEquals(
                """
                error JP1003: referenced method is not declared anywhere above the resolved class
                  symbol:      com.acme.api.OrderPolicy.check(String)
                  class:       com/acme/app/OrderValidator.class
                  from:        app.jar
                  at runtime:  NoSuchMethodError
                  cause:       The class this reference names resolves, and no method of this name and \
                descriptor is declared by it, by any class above it, or by any interface it inherits. A \
                method whose parameters or return type changed is a different method to the JVM, so an \
                overload that merely shares the name does not satisfy the call.
                  observed:    selected lib/api.jar (9f79b9e2)
                               referenced from validate()V
                               the resolved class also declares check()V

                next: Restore the method with the descriptor the call site compiled against, or align \
                the versions.

                1 error, 1 finding
                """,
                rooted(invocation.out()));
        assertEquals("", invocation.err());
    }

    @Test
    void rendersTheVersionedJsonEnvelope() {
        Invocation invocation = check(CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString());

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertEquals(
                """
                {
                  "jarproofJsonVersion": "1",
                  "tool": {
                    "name": "jarproof",
                    "version": "0.0.1-SNAPSHOT"
                  },
                  "request": {
                    "targetJava": 17,
                    "preview": "disabled",
                    "scope": "application"
                  },
                  "findings": [
                    {
                      "code": "JP1003",
                      "severity": "error",
                      "predictedError": "NoSuchMethodError",
                      "artifact": {
                        "artifact": "app.jar",
                        "classEntry": "com/acme/app/OrderValidator.class"
                      },
                      "subject": "com/acme/api/OrderPolicy#check(Ljava/lang/String;)V",
                      "summary": "referenced method is not declared anywhere above the resolved class",
                      "explanation": "The class this reference names resolves, and no method of this \
                name and descriptor is declared by it, by any class above it, or by any interface it \
                inherits. A method whose parameters or return type changed is a different method to the \
                JVM, so an overload that merely shares the name does not satisfy the call.",
                      "evidence": [
                        "selected lib/api.jar (9f79b9e2)",
                        "referenced from validate()V",
                        "the resolved class also declares check()V"
                      ],
                      "remediation": [
                        "Restore the method with the descriptor the call site compiled against, or \
                align the versions."
                      ]
                    }
                  ],
                  "summary": {
                    "info": 0,
                    "warning": 0,
                    "error": 1,
                    "total": 1,
                    "analyzedClasses": 2,
                    "analyzedArtifacts": 2
                  }
                }
                """,
                rooted(invocation.out()));
    }

    @Test
    void rendersTheSameMachineBytesEveryRun() {
        byte[] first = machineBytes();
        byte[] second = machineBytes();

        assertArrayEquals(first, second, new String(first, StandardCharsets.UTF_8));
    }

    @Test
    void rendersWellFormedSarif() {
        Invocation invocation = check(CliFixture.FORMAT, "sarif", PATH_ROOT, workspace.toString());

        Map<?, ?> document = object(JsonScanner.parse(rooted(invocation.out())));
        assertEquals("2.1.0", document.get("version"));
        Map<?, ?> run = object(array(document.get("runs")).get(0));
        assertEquals("jarproof", object(object(run.get("tool")).get("driver")).get("name"));
        Map<?, ?> rule = object(array(object(object(run.get("tool")).get("driver")).get("rules")).get(0));
        assertEquals("JP1003", rule.get("id"));
        Map<?, ?> result = object(array(run.get("results")).get(0));
        assertEquals("JP1003", result.get("ruleId"));
        assertEquals("error", result.get("level"));
        assertEquals(REPORTED_ARTIFACT, uriOf(result));
    }

    @Test
    void locatesSarifResultsInTheSourceTreeTheCallerNames() throws Exception {
        Path sources = sourceTree();
        Path application = CliFixture.tracedApplication(workspace);

        Invocation mapped = sarif(application, SOURCE_ROOT, sources.toString());
        Invocation unmapped = sarif(application);

        Map<?, ?> located = firstResult(mapped.out());
        assertEquals(CliFixture.SOURCE_PATH, uriOf(located), mapped.out());
        assertEquals(CliFixture.CALL_LINE, startLineOf(located), mapped.out());
        assertEquals(REPORTED_ARTIFACT, uriOf(firstResult(unmapped.out())), unmapped.out());
        assertFalse(unmapped.out().contains("region"), unmapped.out());
    }

    @Test
    void reportsTheSourceLineAPersonAndAMachineBothRead() {
        Path application = CliFixture.tracedApplication(workspace);

        Invocation human = CliFixture.invoke(CliFixture.checkArgs(application, CliFixture.library(workspace)));
        Invocation json = CliFixture.invoke(CliFixture.checkArgs(
                application,
                CliFixture.library(workspace),
                CliFixture.FORMAT,
                CliFixture.JSON,
                PATH_ROOT,
                workspace.toString()));

        assertTrue(human.out().contains("  source:      " + CliFixture.SOURCE_FILE + ":" + CliFixture.CALL_LINE),
                human.out());
        assertTrue(json.out().contains("\"sourceFile\": \"" + CliFixture.SOURCE_FILE + "\""), json.out());
        assertTrue(json.out().contains("\"line\": " + CliFixture.CALL_LINE), json.out());
    }

    @Test
    void rendersTheSameSarifBytesEveryRunWithMappingActive() throws Exception {
        String root = sourceTree().toString();
        Path application = CliFixture.tracedApplication(workspace);

        byte[] first = sarif(application, SOURCE_ROOT, root).out().getBytes(StandardCharsets.UTF_8);
        byte[] second = sarif(application, SOURCE_ROOT, root).out().getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(first, second, new String(first, StandardCharsets.UTF_8));
    }

    /**
     * A redirected report leaves both streams empty, which reads as a command that did nothing. One
     * line on the diagnostic stream says what was written and where, and the findings stream stays
     * clean for the consumer that is parsing it.
     */
    @Test
    void writesTheReportWhereItWasAskedAndSaysSo() throws Exception {
        Path report = workspace.resolve("report.json");

        Invocation invocation = check(
                CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString(), OUTPUT, report.toString());

        assertEquals("", invocation.out());
        assertEquals("wrote json report to " + report + "\n", invocation.err());
        assertTrue(Files.readString(report).contains("\"code\": \"JP1003\""), report.toString());
    }

    @Test
    void saysNothingAboutAReportThatWentToTheOutputStream() {
        Invocation invocation = check();

        assertEquals("", invocation.err());
    }

    @Test
    void refusesAnOutputPathWhoseDirectoryIsNotThere() {
        Path report = workspace.resolve("absent").resolve("report.txt");

        Invocation invocation = check(OUTPUT, report.toString());

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Cannot write the report to " + report + ": the directory does not exist\n",
                invocation.err());
    }

    @Test
    void refusesAnOutputPathItIsNotAllowedToWrite() throws IOException {
        Path report = readOnlyFile();

        Invocation invocation = check(OUTPUT, report.toString());

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Cannot write the report to " + report + ": permission was denied\n", invocation.err());
    }

    private Invocation check(String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace), extra));
    }

    private Invocation sarif(Path application, String... extra) {
        List<String> flags = new ArrayList<>(List.of(CliFixture.FORMAT, "sarif", PATH_ROOT, workspace.toString()));
        flags.addAll(List.of(extra));
        return CliFixture.invoke(CliFixture.checkArgs(
                application, CliFixture.library(workspace), flags.toArray(new String[0])));
    }

    /** A source tree holding the application's source file where its package path says it should be. */
    private Path sourceTree() throws IOException {
        Path root = workspace.resolve("src/main/java");
        Path source = root.resolve(CliFixture.SOURCE_PATH);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.acme.app;\n");
        return root;
    }

    /** A file the process may read and not write, which is the other way a write is refused. */
    private Path readOnlyFile() throws IOException {
        Path report = Files.writeString(workspace.resolve("locked.txt"), "\n");
        Files.setPosixFilePermissions(report, Set.of(PosixFilePermission.OWNER_READ));
        return report;
    }

    private static Map<?, ?> firstResult(String document) {
        Map<?, ?> run = object(array(object(JsonScanner.parse(document)).get("runs")).get(0));
        return object(array(run.get("results")).get(0));
    }

    private static int startLineOf(Map<?, ?> result) {
        Map<?, ?> location = object(array(result.get("locations")).get(0));
        return (Integer) object(object(location.get("physicalLocation")).get("region")).get("startLine");
    }

    private byte[] machineBytes() {
        Invocation invocation = check(CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString());
        return invocation.out().getBytes(StandardCharsets.UTF_8);
    }

    private String rooted(String report) {
        return CliFixture.rooted(report, workspace);
    }

    private static String uriOf(Map<?, ?> result) {
        Map<?, ?> location = object(array(result.get("locations")).get(0));
        return (String) object(object(location.get("physicalLocation")).get("artifactLocation")).get("uri");
    }

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        return (List<?>) value;
    }
}
