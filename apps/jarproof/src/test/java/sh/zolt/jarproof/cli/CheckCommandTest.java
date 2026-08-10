package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class CheckCommandTest {
    private static final String PATH_ROOT = "--path-root";
    private static final String OUTPUT = "--output";
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
                    "version": "0.1.0-alpha.1-dev"
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
                    "total": 1
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
        Map<?, ?> run = object(array(document.get("runs")).getFirst());
        assertEquals("jarproof", object(object(run.get("tool")).get("driver")).get("name"));
        Map<?, ?> rule = object(array(object(object(run.get("tool")).get("driver")).get("rules")).getFirst());
        assertEquals("JP1003", rule.get("id"));
        Map<?, ?> result = object(array(run.get("results")).getFirst());
        assertEquals("JP1003", result.get("ruleId"));
        assertEquals("error", result.get("level"));
        assertEquals(REPORTED_ARTIFACT, uriOf(result));
    }

    @Test
    void measuresArtifactPathsFromThePathRootForMachineFormatsOnly() {
        Path application = CliFixture.brokenApplication(workspace);
        Path library = CliFixture.library(workspace);

        Invocation machine = CliFixture.invoke(CliFixture.checkArgs(
                application, library, CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString()));
        Invocation human = CliFixture.invoke(CliFixture.checkArgs(
                application, library, PATH_ROOT, workspace.toString()));

        assertTrue(machine.out().contains("\"artifact\": \"" + REPORTED_ARTIFACT + "\""), machine.out());
        assertTrue(human.out().contains("from:        " + application), human.out());
    }

    @Test
    void writesTheReportWhereItWasAsked() throws Exception {
        Path report = workspace.resolve("report.json");

        Invocation invocation = check(
                CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString(), OUTPUT, report.toString());

        assertEquals("", invocation.out());
        assertTrue(Files.readString(report).contains("\"code\": \"JP1003\""), report.toString());
    }

    @Test
    void refusesAnOutputPathItCannotWrite() {
        Invocation invocation = check(OUTPUT, workspace.resolve("absent").resolve("report.txt").toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("absent"), invocation.err());
    }

    private Invocation check(String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace), extra));
    }

    private byte[] machineBytes() {
        Invocation invocation = check(CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString());
        return invocation.out().getBytes(StandardCharsets.UTF_8);
    }

    private String rooted(String report) {
        return CliFixture.rooted(report, workspace);
    }

    private static String uriOf(Map<?, ?> result) {
        Map<?, ?> location = object(array(result.get("locations")).getFirst());
        return (String) object(object(location.get("physicalLocation")).get("artifactLocation")).get("uri");
    }

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        return (List<?>) value;
    }
}
