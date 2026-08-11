package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CorpusProcess.Outcome;

/**
 * The way a project with existing breakage starts using jarproof, over real artifacts.
 *
 * <p>The recorded file has to be portable, so the artifacts are named with the relative path text a
 * developer standing in the workspace root would type: a fingerprint keeps the caller's path text
 * verbatim, so a baseline recorded from absolute paths is valid on exactly one machine and worthless
 * in a repository. That makes the working directory part of the contract, which is why this is the one
 * class that runs the packaged command line as a process instead of calling the entry point in
 * process. The recorded fingerprint is then asserted in full, because it is the identity that decides
 * whether a finding is the one already accepted or a new one.
 *
 * <p>The ratchet is tested from both sides. The accepted finding must stop failing the run and be
 * counted as suppressed rather than silently dropped, and a second, different breakage on the same
 * classpath must fail the run and be the only thing rendered.
 */
final class BaselineCorpusTest {
    private static final String CONSUMER = "missing-method-consumer";
    private static final String BROKEN_API = "missing-method-api-v2";
    private static final String NEWER = "newer-bytecode";
    private static final String BASELINE_FILE = "jarproof-baseline.json";
    private static final String ACCEPTED = "suppressed 1 accepted finding; 0 baseline entries are stale\n";
    /**
     * A ratcheted run still reports what it examined. The two fixture artifacts declare one class
     * each, so the line that used to be a bare {@code no findings} now distinguishes this run from one
     * that never opened them.
     */
    private static final String NOTHING_NEW = "no findings — analyzed 2 classes across 2 artifacts\n";
    private static final String WROTE_ONE = "wrote 1 accepted finding to ";
    private static final String FINGERPRINTS = "fingerprints";
    private static final String RECORDED = "JP1003|fixtures/missing-method-consumer/target/"
            + "jarproof-fixture-missing-method-consumer-0.0.1-SNAPSHOT.jar"
            + "|sh/zolt/jarproof/fixtures/missingmethod/consumer/OrderReport.class"
            + "|sh/zolt/jarproof/fixtures/missingmethod/OrderPolicy#describe(Ljava/lang/String;I)Ljava/lang/String;";

    @TempDir
    Path workspace;

    @Test
    void recordsThePathTextTheCallerWroteAndAcceptsWhatItRecorded() throws IOException {
        Outcome recorded = record();

        assertEquals(0, recorded.exitCode(), recorded.err());
        assertEquals("", recorded.out());
        assertEquals(WROTE_ONE + baseline() + "\n", recorded.err());
        assertEquals(List.of(RECORDED), fingerprints());

        Outcome accepted = CorpusProcess.jarproof(workspace, against(List.of()));

        assertEquals(0, accepted.exitCode(), accepted.err());
        assertEquals(NOTHING_NEW, accepted.out());
        assertEquals(ACCEPTED, accepted.err());
    }

    @Test
    void rendersOnlyTheBreakageTheBaselineDoesNotAccept() throws IOException {
        record();

        Outcome check = CorpusProcess.jarproof(
                workspace, against(List.of(CorpusCommand.CLASSPATH, relative(NEWER))));

        assertEquals(1, check.exitCode(), check.err());
        assertTrue(check.out().contains("JP3001"), check.out());
        assertFalse(check.out().contains("JP1003"), check.out());
        assertTrue(check.out().endsWith("1 error, 1 finding\n"), check.out());
        assertEquals(ACCEPTED, check.err());
    }

    /**
     * The same acceptance, recorded from absolute paths and read by a run that names those files the
     * way a developer standing in the workspace would.
     *
     * <p>This is the portability claim in one test. A fingerprint is measured from the path root, so
     * the spelling the recording happened to be given does not travel into the file -- which is what
     * lets a baseline recorded by a build script holding absolute paths be committed and then read by
     * a developer, or by CI, from a relative command line.
     */
    @Test
    void acceptsARelativeRunAgainstARecordingMadeFromAbsolutePaths() {
        Outcome recorded = CorpusProcess.jarproof(workspace, List.of(
                CorpusCommand.BASELINE,
                CorpusCommand.APPLICATION, absolute(CONSUMER),
                CorpusCommand.CLASSPATH, absolute(BROKEN_API),
                CorpusCommand.TARGET_JAVA, CorpusCommand.JAVA_17,
                CorpusCommand.PATH_ROOT, FixtureCorpus.workspaceRoot().toString(),
                CorpusCommand.OUT, baseline().toString()));

        assertEquals(0, recorded.exitCode(), recorded.err());

        Outcome accepted = CorpusProcess.jarproof(workspace, against(List.of()));

        assertEquals(0, accepted.exitCode(), accepted.err());
        assertEquals(NOTHING_NEW, accepted.out());
        assertEquals(ACCEPTED, accepted.err());
    }

    private Outcome record() {
        return CorpusProcess.jarproof(workspace, List.of(
                CorpusCommand.BASELINE,
                CorpusCommand.APPLICATION, relative(CONSUMER),
                CorpusCommand.CLASSPATH, relative(BROKEN_API),
                CorpusCommand.TARGET_JAVA, CorpusCommand.JAVA_17,
                CorpusCommand.OUT, baseline().toString()));
    }

    /** The check the baseline was recorded from, plus whatever a test adds to its classpath. */
    private List<String> against(List<String> extra) {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.CHECK,
                CorpusCommand.APPLICATION, relative(CONSUMER),
                CorpusCommand.CLASSPATH, relative(BROKEN_API)));
        arguments.addAll(extra);
        arguments.addAll(List.of(
                CorpusCommand.TARGET_JAVA, CorpusCommand.JAVA_17,
                CorpusCommand.BASELINE_FLAG, baseline().toString()));
        return arguments;
    }

    private List<?> fingerprints() throws IOException {
        Object document = JsonScanner.parse(Files.readString(baseline()));
        return (List<?>) ((Map<?, ?>) document).get(FINGERPRINTS);
    }

    private Path baseline() {
        return workspace.resolve(BASELINE_FILE);
    }

    private static String relative(String member) {
        return FixtureCorpus.relativePath(FixtureCorpus.jar(member));
    }

    private static String absolute(String member) {
        return FixtureCorpus.jar(member).toString();
    }
}
