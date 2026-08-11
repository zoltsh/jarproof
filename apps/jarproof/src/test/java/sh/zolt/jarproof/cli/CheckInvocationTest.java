package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class CheckInvocationTest {
    private static final String FAIL_ON = "--fail-on";
    private static final String SCOPE = "--scope";
    private static final String HEALTHY = "com/acme/app/Healthy";

    /** What a clean run over one single-class artifact says it examined. */
    private static final String NOTHING_IN_ONE_CLASS = "no findings — analyzed 1 class across 1 artifact\n";

    @TempDir
    Path workspace;

    @Test
    void reportsNothingForAHealthyClasspath() {
        Path application = CliFixture.jar(workspace, "healthy.jar",
                CliFixture.entries(HEALTHY + ".class", CliFixture.classFile(HEALTHY, CliFixture.JAVA_17_MAJOR)));

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(NOTHING_IN_ONE_CLASS, invocation.out());
    }

    /**
     * The machine envelope of the same clean run states the same tallies. A consumer reading
     * {@code "total": 0} beside one artifact really opened has a verified classpath; the same zero
     * with no tallies at all could be a run that never read anything.
     */
    @Test
    void statesWhatACleanRunExaminedInTheMachineEnvelopeToo() {
        Path application = CliFixture.jar(workspace, "healthy.jar",
                CliFixture.entries(HEALTHY + ".class", CliFixture.classFile(HEALTHY, CliFixture.JAVA_17_MAJOR)));

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                CliFixture.FORMAT,
                CliFixture.JSON);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(
                invocation.out().endsWith("\n  \"summary\": {\n    \"info\": 0,\n    \"warning\": 0,\n"
                        + "    \"error\": 0,\n    \"total\": 0,\n    \"analyzedClasses\": 1,\n"
                        + "    \"analyzedArtifacts\": 1\n  }\n}\n"),
                invocation.out());
    }

    @Test
    void neverFailsWhenTheThresholdSaysNever() {
        Invocation invocation = check(FAIL_ON, "never");

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("JP1003"), invocation.out());
    }

    @Test
    void acceptsPreviewClassfilesForTheTargetReleaseWhenAsked() {
        Path application = CliFixture.jar(workspace, "preview.jar",
                CliFixture.entries(HEALTHY + CliFixture.CLASS_SUFFIX, CliFixture.previewClassFile(HEALTHY)));

        Invocation withoutPreview = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);
        Invocation withPreview = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                "--enable-preview");

        assertEquals(1, withoutPreview.exitCode(), withoutPreview.err());
        assertTrue(withoutPreview.out().contains("JP3002"), withoutPreview.out());
        assertEquals(0, withPreview.exitCode(), withPreview.err());
        assertEquals(NOTHING_IN_ONE_CLASS, withPreview.out());
    }

    @Test
    void widensTheScopeWhenAsked() {
        Invocation invocation = check(SCOPE, "all");

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("JP1003"), invocation.out());
    }

    /**
     * The third scope parses and reaches the engine. The broken call the fixture makes sits in the
     * consumer's own {@code main}, which the entry surface presumes live, so the finding survives the
     * reachability filter and the run still fails.
     */
    @Test
    void provesReachabilityWhenAsked() {
        Invocation invocation = check(SCOPE, "reachable");

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("JP1003"), invocation.out());
    }

    @Test
    void refusesAnApplicationThatIsNotThere() {
        Path absent = workspace.resolve("absent.jar");

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                absent.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "This application artifact does not exist or cannot be read: " + absent + "\n",
                invocation.err());
    }

    @Test
    void refusesATargetReleaseWithNoBundledSymbols() {
        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.TARGET_JAVA,
                "20");

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Java 20 has no bundled JDK symbols; bundled releases are 8, 11, 17, 21, and 25."
                        + " Pass --jdk <path> to read symbols from a local JDK.\n",
                invocation.err());
    }

    /**
     * A target release that is not a number is refused in the same voice as a number no runtime ever
     * had: one line naming the flag's own meaning and the text that failed it.
     */
    @Test
    void refusesATargetReleaseThatIsNotANumber() {
        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.TARGET_JAVA,
                "abc");

        assertEquals(2, invocation.exitCode());
        assertEquals("Target Java release must be a number: abc\n", invocation.err());
        assertEquals("", invocation.out());
    }

    @Test
    void namesTheTargetReleaseAsRequiredInItsOwnHelp() {
        Invocation invocation = CliFixture.invoke(CliFixture.CHECK, "--help");

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("Required."), invocation.out());
    }

    @Test
    void refusesAReleaseNoRuntimeEverHad() {
        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.TARGET_JAVA,
                "7");

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("8 or newer"), invocation.err());
    }

    @Test
    void refusesAFlagValueOutsideItsVocabulary() {
        Invocation invocation = check(CliFixture.FORMAT, "yaml");

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("yaml is not one of: human|json|sarif"), invocation.err());
    }

    @Test
    void refusesAVocabularySpeltInTheWrongCase() {
        Invocation invocation = check(SCOPE, "ALL");

        assertEquals(2, invocation.exitCode());
        assertTrue(
                invocation.err().contains("ALL is not one of: application|all|reachable"),
                invocation.err());
    }

    @Test
    void keepsEveryFindingOffTheDiagnosticStream() {
        Invocation invocation = check();

        assertEquals("", invocation.err());
        assertFalse(invocation.out().isEmpty());
    }

    @Test
    void readsAnAtSignOutsideTheClasspathAsPlainText() {
        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                "@wherever",
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "This application artifact does not exist or cannot be read: @wherever\n", invocation.err());
    }

    @Test
    void keepsEveryApplicationInTheOrderItWasGiven() {
        Path first = CliFixture.brokenApplication(workspace);
        Path second = CliFixture.jar(workspace, "extra.jar",
                CliFixture.entries(HEALTHY + ".class", CliFixture.classFile(HEALTHY, CliFixture.JAVA_17_MAJOR)));

        Invocation invocation = CliFixture.invoke(List.of(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                first.toString(),
                CliFixture.APPLICATION,
                second.toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17).toArray(new String[0]));

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("from:        " + first), invocation.out());
    }

    private Invocation check(String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace), extra));
    }
}
