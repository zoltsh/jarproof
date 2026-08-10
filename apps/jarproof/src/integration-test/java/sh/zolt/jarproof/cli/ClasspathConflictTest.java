package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CorpusCheck.Reported;

/**
 * What the classpath itself decides, and where it decides nothing.
 *
 * <p>Two artifacts declaring the same class differently is a warning while the classpath settles
 * which one wins: the first entry wins outright, which is unfortunate but knowable. Staging the same
 * two artifacts into one directory and naming them with a single wildcard removes exactly that
 * property, because the launcher never promised an expansion order, and the finding becomes an error
 * that replaces the warning rather than joining it. Running both shapes in one class is what makes
 * the difference legible.
 *
 * <p>The application is a member that declares none of the conflicting classes, so the duplicate is
 * decided by the classpath entries under test instead of by the application always winning.
 */
final class ClasspathConflictTest {
    private static final String NEUTRAL = "nestmates";
    private static final String COPY_A = "duplicate-class-a";
    private static final String COPY_B = "duplicate-class-b";
    private static final String SPLIT_A = "split-package-a";
    private static final String SPLIT_B = "split-package-b";
    private static final String NEWER = "newer-bytecode";
    private static final String DUPLICATE_CLASS = "sh/zolt/jarproof/fixtures/duplicate/Greeting";
    private static final String DUPLICATE_PACKAGE = "sh/zolt/jarproof/fixtures/duplicate";
    private static final String SPLIT_PACKAGE = "sh/zolt/jarproof/fixtures/split";
    private static final String NONE = "None";
    private static final String INFO = "info";
    private static final String WARNING = "warning";
    private static final String ERROR = "error";
    private static final String STAGED = "staged";

    @TempDir
    Path workspace;

    @Test
    void namesTheWinnerWhenTheClasspathOrderDecidesIt() {
        Path first = FixtureCorpus.jar(COPY_A);

        CorpusCheck check = check("explicit", List.of(
                CorpusCommand.CLASSPATH, first.toString(),
                CorpusCommand.CLASSPATH, FixtureCorpus.jar(COPY_B).toString()));

        assertEquals(
                List.of(
                        new Reported("JP2002", WARNING, NONE, FixtureCorpus.relativePath(first), DUPLICATE_CLASS),
                        new Reported("JP2003", INFO, NONE, FixtureCorpus.relativePath(first), DUPLICATE_PACKAGE)),
                check.findings(),
                check.report());
        assertEquals(0, check.exitCode(), check.err());
    }

    @Test
    void refusesToNameAWinnerAWildcardDoesNotDecide() {
        String wildcard = FixtureCorpus.stage(
                workspace.resolve(STAGED), List.of(FixtureCorpus.jar(COPY_A), FixtureCorpus.jar(COPY_B)));

        CorpusCheck check = check("wildcard", List.of(CorpusCommand.CLASSPATH, wildcard));

        assertEquals(
                List.of("JP2003 " + DUPLICATE_PACKAGE, "JP2006 " + DUPLICATE_CLASS),
                check.signatures(),
                check.report());
        assertEquals(
                List.of("JP2006 " + DUPLICATE_CLASS),
                check.errors().stream().map(Reported::signature).toList(),
                check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    @Test
    void reportsAPlainSplitPackageAsInformation() {
        Path first = FixtureCorpus.jar(SPLIT_A);

        CorpusCheck check = check("split", List.of(
                CorpusCommand.CLASSPATH, first.toString(),
                CorpusCommand.CLASSPATH, FixtureCorpus.jar(SPLIT_B).toString()));

        assertEquals(
                List.of(new Reported("JP2003", INFO, NONE, FixtureCorpus.relativePath(first), SPLIT_PACKAGE)),
                check.findings(),
                check.report());
        assertEquals(0, check.exitCode(), check.err());
    }

    @Test
    void reportsBytecodeTheTargetRuntimeWouldRefuse() {
        Path newer = FixtureCorpus.jar(NEWER);

        CorpusCheck check = CorpusCheck.reporting(
                workspace.resolve(NEWER + ".json"),
                List.of(CorpusCommand.APPLICATION, newer.toString()));

        assertEquals(
                List.of(new Reported(
                        "JP3001",
                        ERROR,
                        "UnsupportedClassVersionError",
                        FixtureCorpus.relativePath(newer),
                        "sh/zolt/jarproof/fixtures/newerbytecode/ModernPoint")),
                check.findings(),
                check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    private CorpusCheck check(String name, List<String> classpath) {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.APPLICATION, FixtureCorpus.jar(NEUTRAL).toString(),
                CorpusCommand.SCOPE, CorpusCommand.ALL));
        arguments.addAll(classpath);
        return CorpusCheck.reporting(workspace.resolve(name + ".json"), arguments);
    }
}
