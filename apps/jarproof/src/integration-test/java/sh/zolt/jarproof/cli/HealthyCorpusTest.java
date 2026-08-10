package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
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
 * <p>The same application is then launched on the same classpath. Zero errors from a corpus that does
 * not actually work would prove nothing at all.
 */
final class HealthyCorpusTest {
    private static final String EXPECTED_OUTPUT = "[beta, alpha]\n7\n";
    private static final String REPORT = "healthy-corpus.json";
    private static final String FAILED = "The healthy corpus produced errors, so a false positive"
            + " reached a real classpath:\n";

    @TempDir
    Path workspace;

    @Test
    void reportsNoErrorsForAnApplicationThatReallyUsesItsLibraries() {
        Path application = HealthyApplication.jar(workspace);
        List<Path> libraries = LibraryClosure.jars();

        CorpusCheck check = CorpusCheck.reporting(workspace.resolve(REPORT), against(application, libraries));

        assertEquals(List.of(), check.errors(), () -> FAILED + summary(check) + check.report());
    }

    @Test
    void launchesThatApplicationOnTheSameClasspath() {
        Path application = HealthyApplication.jar(workspace);
        StringJoiner classpath = new StringJoiner(File.pathSeparator);
        classpath.add(application.toString());
        LibraryClosure.jars().forEach(library -> classpath.add(library.toString()));

        Outcome outcome = CorpusProcess.launch(workspace, classpath.toString(), HealthyApplication.MAIN_CLASS);

        assertEquals(0, outcome.exitCode(), outcome.err());
        assertEquals(EXPECTED_OUTPUT, outcome.out(), outcome.err());
    }

    private static List<String> against(Path application, List<Path> libraries) {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.APPLICATION, application.toString(),
                CorpusCommand.SCOPE, CorpusCommand.APPLICATION_ONLY));
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
