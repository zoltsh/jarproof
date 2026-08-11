package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

/**
 * What a check says when there was nothing to check.
 *
 * <p>{@code no findings} is the most reassuring thing jarproof prints, and it is a lie when the
 * application root held no bytecode: nothing was examined, so nothing could be found. JP3007 makes
 * the difference visible without failing the run, because an empty root is legal and the tool has no
 * business deciding it was a mistake.
 */
final class EmptyRootTest {
    private static final String CODE = "JP3007";
    private static final String SUMMARY = "info JP3007: application root contributes no classes";

    @TempDir
    Path workspace;

    @Test
    void reportsAnEmptyDirectoryRootInsteadOfSayingNothingWasFound() throws IOException {
        Path root = Files.createDirectory(workspace.resolve("classes"));

        Invocation invocation = check(root);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().startsWith(SUMMARY + "\n"), invocation.out());
        assertTrue(invocation.out().contains("from:        " + root), invocation.out());
        assertTrue(invocation.out().endsWith("1 info, 1 finding\n"), invocation.out());
    }

    @Test
    void reportsAnArchiveRootThatCarriesOnlyResources() {
        Path archive = CliFixture.jar(
                workspace, "resources.jar", CliFixture.entries("META-INF/NOTICE", "notice".getBytes(StandardCharsets.UTF_8)));

        Invocation invocation = check(archive);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains(CODE), invocation.out());
    }

    /** One finding per empty root, and none for the root that carries classes. */
    @Test
    void namesEveryEmptyRootAndNoOther() throws IOException {
        Path empty = Files.createDirectory(workspace.resolve("empty"));
        Path alsoEmpty = Files.createDirectory(workspace.resolve("also-empty"));

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                empty.toString(),
                CliFixture.APPLICATION,
                alsoEmpty.toString(),
                CliFixture.APPLICATION,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("from:        " + empty), invocation.out());
        assertTrue(invocation.out().contains("from:        " + alsoEmpty), invocation.out());
        assertTrue(invocation.out().endsWith("2 infos, 2 findings\n"), invocation.out());
    }

    /**
     * A classpath entry with no classes is ordinary, and saying anything about it would be noise. It
     * was still read, so it is counted: the tally says two artifacts were opened and one class came
     * out of them, which is the honest description of this classpath.
     */
    @Test
    void saysNothingAboutAnEmptyClasspathEntry() throws IOException {
        Path empty = Files.createDirectory(workspace.resolve("empty"));

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.library(workspace).toString(),
                CliFixture.CLASSPATH,
                empty.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("no findings — analyzed 1 class across 2 artifacts\n", invocation.out());
    }

    @Test
    void staysAbsentFromARunWhoseRootsCarryClasses() {
        Invocation invocation = CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace)));

        assertFalse(invocation.out().contains(CODE), invocation.out());
    }

    @Test
    void explainsItselfLikeEveryOtherCode() {
        Invocation invocation = CliFixture.invoke("explain", CODE);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().startsWith(CODE + " application root contributes no classes\n"),
                invocation.out());
    }

    private Invocation check(Path application) {
        return CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);
    }
}
