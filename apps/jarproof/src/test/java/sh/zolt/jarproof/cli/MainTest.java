package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class MainTest {
    private static final String HELP = "--help";
    private static final String USAGE = "Usage: jarproof";
    private static final String POINTS_AT_CHECK = "Run 'jarproof check --help' for details.\n";

    @TempDir
    Path workspace;

    @Test
    void printsFocusedHelp() {
        Invocation invocation = CliFixture.invoke(HELP);

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.out().contains("Find JAR hell before production."));
        assertTrue(invocation.out().contains(USAGE));
        assertEquals("", invocation.err());
    }

    @Test
    void showsHelpWhenNoCommandIsGiven() {
        Invocation invocation = CliFixture.invoke();

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.out().contains(USAGE));
    }

    @Test
    void offersEveryCommandItImplements() {
        String help = CliFixture.invoke(HELP).out();

        assertTrue(help.contains("check"), help);
        assertTrue(help.contains("baseline"), help);
        assertTrue(help.contains("inspect"), help);
        assertTrue(help.contains("explain"), help);
    }

    @Test
    void printsTheProductVersionBanner() {
        Invocation invocation = CliFixture.invoke("--version");

        assertEquals(0, invocation.exitCode());
        assertEquals("jarproof 0.0.1-SNAPSHOT\n", invocation.out());
    }

    /**
     * A subcommand's own help is the one a reader is pointed at, so it has to answer as cleanly as the
     * root's: on the output stream, with the invocation succeeding.
     */
    @Test
    void printsASubcommandHelpTheRefusalPointsAt() {
        Invocation invocation = CliFixture.invoke(CliFixture.CHECK, HELP);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("Usage: jarproof check"), invocation.out());
        assertEquals("", invocation.err());
    }

    /**
     * A mistyped command is three lines: what could not be placed, the command it probably meant, and
     * where the full help is. The whole usage text used to follow, on the diagnostic stream, which
     * buried the one line that mattered under fifty that did not.
     */
    @Test
    void rejectsACommandThatDoesNotExist() {
        Invocation invocation = CliFixture.invoke("chek");

        assertEquals(2, invocation.exitCode());
        assertEquals(
                """
                Unmatched argument at index 0: 'chek'
                Did you mean: jarproof check?
                Run 'jarproof --help' for details.
                """,
                invocation.err());
        assertEquals("", invocation.out());
    }

    @Test
    void rejectsACheckWithoutTheFlagsItNeeds() {
        Invocation invocation = CliFixture.invoke(CliFixture.CHECK);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("--application"), invocation.err());
        assertTrue(invocation.err().contains("--target-java"), invocation.err());
        assertTrue(invocation.err().endsWith(POINTS_AT_CHECK), invocation.err());
        assertEquals(2, invocation.err().lines().count(), invocation.err());
    }

    /** The value an unknown option consumed is not itself an unknown option. */
    @Test
    void namesOnlyTheOptionItDidNotRecognise() {
        Invocation invocation = complete("--bogus", "somewhere");

        assertEquals(2, invocation.exitCode());
        assertEquals("Unknown option: '--bogus'\n" + POINTS_AT_CHECK, invocation.err());
    }

    @Test
    void namesEveryOptionItDidNotRecogniseWhenThereAreSeveral() {
        Invocation invocation = complete("--bogus", "--nonsense");

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Unknown options: '--bogus', '--nonsense'\n" + POINTS_AT_CHECK, invocation.err());
    }

    /** A misspelling close to a real flag keeps the suggestion the parser can make. */
    @Test
    void suggestsTheFlagAMisspellingMeant() {
        Invocation invocation = complete("--applicaton", "app.jar");

        assertEquals(2, invocation.exitCode());
        assertEquals(
                """
                Unknown option: '--applicaton'
                Possible solutions: --application
                """
                        + POINTS_AT_CHECK,
                invocation.err());
    }

    @Test
    void keepsEveryRefusalOffTheOutputStreamAndUnderThreeLines() {
        Invocation invocation = CliFixture.invoke("inspect", CliFixture.FORMAT, "yaml", "app.jar");

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
        assertTrue(invocation.err().lines().count() <= 3, invocation.err());
    }

    /**
     * A complete {@code check} command line plus whatever a test adds, so the parser reaches the
     * argument under test instead of stopping at a missing required flag.
     */
    private Invocation complete(String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace), extra));
    }
}
