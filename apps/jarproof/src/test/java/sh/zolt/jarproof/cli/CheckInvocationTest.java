package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class CheckInvocationTest {
    private static final String FAIL_ON = "--fail-on";
    private static final String SCOPE = "--scope";
    private static final String HEALTHY = "com/acme/app/Healthy";

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
        assertEquals("no findings\n", invocation.out());
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
        assertEquals("no findings\n", withPreview.out());
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
    void expandsAClasspathListFile() throws IOException {
        Path application = CliFixture.brokenApplication(workspace);
        Path library = CliFixture.library(workspace);
        Path list = Files.writeString(
                workspace.resolve("classpath.txt"),
                "\n   " + library + "   \n\n",
                StandardCharsets.UTF_8);

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.CLASSPATH,
                "@" + list,
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("selected " + library), invocation.out());
    }

    @Test
    void refusesAClasspathListThatIsNotThere() {
        Path list = workspace.resolve("absent.txt");

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.CLASSPATH,
                "@" + list,
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains(list.toString()), invocation.err());
        assertEquals("", invocation.out());
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
                "21");

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Java 21 has no bundled JDK symbols; bundled releases are [17]\n",
                invocation.err());
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
