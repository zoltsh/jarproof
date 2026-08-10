package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CorpusCheck.Reported;
import sh.zolt.jarproof.cli.CorpusProcess.Outcome;

/**
 * The release-blocking bar from DESIGN section 4: a healthy real-world classpath produces no errors.
 *
 * <p>This is the test that decides whether jarproof is usable. Mature libraries reference classes
 * that are absent at run time on purpose — optional integrations, reflection-guarded probes, plain
 * dead paths — and a verifier that reports all of it is technically correct and practically
 * worthless. So the application here calls Guava, Jackson, and Netty for real, and it is checked
 * against the whole resolved closure those libraries came with.
 *
 * <p>If this fails, the false-positive discipline broke, and no amount of passing fixture tests makes
 * up for it. The failure message therefore carries the complete report rather than a count, because
 * the next question is always which finding and why.
 *
 * <p>Zero errors is the bar, but it is not the whole signal. The same closure is therefore checked
 * twice: once at {@code --scope application}, which is the default a user gets, and once at
 * {@code --scope all}, which inspects every library-to-library origin as well. Both runs are pinned to
 * exact tallies of every severity, because a false positive arrives as a warning long before it
 * arrives as an error, and a ceiling that only counts errors would let the noise grow underneath it
 * until a later change promoted one. Pinning what is benign today is what makes tomorrow's addition
 * visible.
 *
 * <p>The same application is then launched on the same classpath. Zero errors from a corpus that does
 * not actually work would prove nothing at all.
 */
final class HealthyCorpusTest {
    private static final String EXPECTED_OUTPUT = "[beta, alpha]\n7\n";
    private static final String APPLICATION_REPORT = "healthy-corpus-application.json";
    private static final String CLOSURE_REPORT = "healthy-corpus-all.json";
    private static final String FAILED = "The healthy corpus produced errors, so a false positive"
            + " reached a real classpath:\n";
    private static final String MOVED = "The healthy corpus no longer reports what it is pinned to,"
            + " so a check changed what it says about real libraries:\n";

    /*
     * The pinned tallies, and the maintenance contract for them.
     *
     * These numbers move for exactly two reasons: the corpus zolt.lock resolves changed, or a check
     * deliberately changed what it says. Nothing else is a legitimate reason to edit them, and in
     * particular a number is never re-copied to make a run green. Either way the drift lines in the
     * failure message are the review artifact: they name every code and severity that moved and by how
     * much, so the diff in a pull request is a claim about library behaviour that a reviewer can agree
     * or disagree with rather than an unexplained integer.
     *
     * What they are today, which is why the shape is worth pinning at all. Application scope sees only
     * advisories: eleven artifacts in the closure mix bytecode levels (JP3005), which is ordinary in
     * released and repackaged jars and says nothing about linkage. Inspecting every library origin adds
     * warnings and no errors, and they are the textbook cases DESIGN section 4 exists for — Netty's
     * optional log4j2 integration references a logging API nobody put on this classpath, and the JUnit
     * console jar references the Kotlin runtime it only needs for Kotlin callers. Neither ever executes
     * here, which is exactly why library-only evidence is warning-level and errors stay at zero.
     *
     * The closure is every jar this test JVM was launched with, so the runner and the engine's own ASM
     * are part of it as honestly as Guava is. That is a wider corpus, not a leak: they are locked
     * releases like the rest, and they contribute the same kind of optional-dependency evidence.
     */
    private static final SeverityTally APPLICATION_ORIGIN =
            SeverityTally.pinning(0, 0, 11, Map.of("JP3005 info", 11));
    private static final SeverityTally EVERY_ORIGIN = SeverityTally.pinning(0, 41, 11, Map.of(
            "JP1001 warnings", 34,
            "JP1003 warnings", 7,
            "JP3005 info", 11));

    @TempDir
    static Path workspace;

    private static Path application;
    private static List<Path> libraries;

    /** One synthesis for every run below: the same archive, checked twice and then launched. */
    @BeforeAll
    static void buildTheApplicationOnce() {
        application = HealthyApplication.jar(workspace);
        libraries = LibraryClosure.jars();
    }

    /** The default scope, which is the one the release bar is stated about. */
    @Test
    void reportsNoErrorsForAnApplicationThatReallyUsesItsLibraries() {
        CorpusCheck check = CorpusCheck.reporting(
                workspace.resolve(APPLICATION_REPORT), against(CorpusCommand.APPLICATION_ONLY));

        assertEquals(List.of(), check.errors(), () -> FAILED + summary(check) + check.report());
        assertEquals(0, check.exitCode(), check.err());
        assertPinned(APPLICATION_ORIGIN, check);
    }

    /** Every library-to-library origin, where the ceiling is a tally because the bar cannot be zero. */
    @Test
    void reportsThePinnedTalliesWhenEveryLibraryOriginIsInspected() {
        CorpusCheck check =
                CorpusCheck.reporting(workspace.resolve(CLOSURE_REPORT), against(CorpusCommand.ALL));

        assertEquals(List.of(), check.errors(), () -> FAILED + summary(check) + check.report());
        assertEquals(0, check.exitCode(), check.err());
        assertPinned(EVERY_ORIGIN, check);
    }

    @Test
    void launchesThatApplicationOnTheSameClasspath() {
        StringJoiner classpath = new StringJoiner(File.pathSeparator);
        classpath.add(application.toString());
        libraries.forEach(library -> classpath.add(library.toString()));

        Outcome outcome = CorpusProcess.launch(workspace, classpath.toString(), HealthyApplication.MAIN_CLASS);

        assertEquals(0, outcome.exitCode(), outcome.err());
        assertEquals(EXPECTED_OUTPUT, outcome.out(), outcome.err());
    }

    /** Compares parsed findings against the pin, and explains any disagreement before the report. */
    private static void assertPinned(SeverityTally pinned, CorpusCheck check) {
        List<String> drift = pinned.diff(SeverityTally.of(check.findings()));

        assertTrue(drift.isEmpty(), () -> MOVED + String.join("\n", drift) + "\n\n" + check.report());
    }

    private static List<String> against(String scope) {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.APPLICATION, application.toString(),
                CorpusCommand.SCOPE, scope));
        for (Path library : libraries) {
            arguments.addAll(List.of(CorpusCommand.CLASSPATH, library.toString()));
        }
        return arguments;
    }

    private static String summary(CorpusCheck check) {
        StringJoiner lines = new StringJoiner("\n", "", "\n");
        for (Reported finding : check.findings()) {
            lines.add(finding.severity() + " " + finding.signature() + " in " + finding.artifact());
        }
        return lines.toString();
    }
}
