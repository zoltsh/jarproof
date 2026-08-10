package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

/**
 * Which paths a report measures from {@code --path-root}, and which it repeats verbatim.
 *
 * <p>The rule is per format rather than per field: a machine consumer reads every path measured, and
 * a person reads every path as they typed it. A report that measured the artifact field and left an
 * absolute path in the sentence beside it would satisfy neither -- it would be unstable across
 * machines for the consumer and unfamiliar for the person.
 */
final class MachinePathTest {
    private static final String PATH_ROOT = "--path-root";
    private static final String REPORTED_ARTIFACT = "app.jar";

    @TempDir
    Path workspace;

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

    /**
     * The evidence beside a measured artifact is measured with it. A caller who names an absolute
     * classpath would otherwise get a root-relative {@code artifact} and, one line below it, a
     * sentence naming a directory that exists on exactly one machine.
     */
    @Test
    void measuresThePathsInsideEvidenceForMachineFormatsToo() {
        Path library = CliFixture.library(workspace);

        Invocation invocation = check(CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, workspace.toString());

        assertEquals(
                List.of(
                        "selected lib/api.jar (9f79b9e2)",
                        "referenced from validate()V",
                        "the resolved class also declares check()V"),
                evidenceOf(invocation.out()),
                invocation.out());
        assertFalse(invocation.out().contains(library.toString()), invocation.out());
    }

    /** The human report repeats what the caller typed, evidence included. */
    @Test
    void keepsThePathsInsideEvidenceAsTypedForAPerson() {
        Path library = CliFixture.library(workspace);

        Invocation invocation = check(PATH_ROOT, workspace.toString());

        assertTrue(invocation.out().contains("selected " + library), invocation.out());
    }

    /** An artifact the root cannot express is quoted, not measured, in evidence as in the artifact. */
    @Test
    void keepsAnEvidencePathThatEscapesTheRootAsTheCallerWroteIt() throws IOException {
        Path root = Files.createDirectory(workspace.resolve("root"));
        Path library = CliFixture.library(workspace);

        Invocation invocation = CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace),
                library,
                CliFixture.FORMAT,
                CliFixture.JSON,
                PATH_ROOT,
                root.toString()));

        assertTrue(invocation.out().contains("selected " + library + " ("), invocation.out());
        assertTrue(invocation.out().contains("\"artifact\": \"" + workspace + "/app.jar\""), invocation.out());
    }

    /** A root that is not there would otherwise be reported as every artifact sitting below it. */
    @Test
    void refusesAPathRootThatIsNotADirectory() {
        Path absent = workspace.resolve("nowhere");

        Invocation invocation = check(CliFixture.FORMAT, CliFixture.JSON, PATH_ROOT, absent.toString());

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "This path root is not a directory that exists: " + absent + "\n", invocation.err());
        assertEquals("", invocation.out());
    }

    /** The human report is not the determinism contract, so a bad root refuses there too. */
    @Test
    void refusesAPathRootWhateverTheFormatWouldHaveDoneWithIt() {
        Invocation invocation = check(PATH_ROOT, workspace.resolve("nowhere").toString());

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
    }

    private Invocation check(String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace), extra));
    }

    /** Every evidence line of the first finding, read back as parsed values. */
    private static List<String> evidenceOf(String document) {
        Map<?, ?> parsed = (Map<?, ?>) JsonScanner.parse(document);
        Map<?, ?> finding = (Map<?, ?>) ((List<?>) parsed.get("findings")).get(0);
        return ((List<?>) finding.get("evidence")).stream().map(String.class::cast).toList();
    }
}
