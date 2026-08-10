package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

/**
 * What {@code @file} means, and the one component that gets to decide it.
 *
 * <p>A classpath list is jarproof's own feature: {@link ClasspathEntries} reads it, one entry per
 * line, for {@code --classpath} and nowhere else. The command-line parser has a feature of the same
 * spelling that splices a file's lines into the argument array, and the two cannot both be right --
 * the parser's version turns a two-entry list into one classpath value and one stray argument, and
 * turns an empty list into no value at all.
 *
 * <p>Every run here goes through the process entry point rather than through a command object,
 * because switching that feature off is per-command state and the entry point is where the whole
 * hierarchy is told: a test that called the reader directly would pass whatever the parser had done
 * to the argument first, which is the only way this can break.
 */
final class ClasspathListTest {
    private static final String LIST = "classpath.txt";
    private static final String AT = "@";

    @TempDir
    Path workspace;

    /** A list naming two entries is two entries, not one entry and an unmatched argument. */
    @Test
    void readsEveryLineOfAListAsAClasspathEntry() throws IOException {
        Path library = CliFixture.library(workspace);
        Path newer = CliFixture.jar(workspace, "newer.jar",
                CliFixture.entries("com/acme/app/Newer.class", CliFixture.classFile("com/acme/app/Newer", 65)));
        Path list = list(library + "\n" + newer + "\n");

        Invocation invocation = check(list);

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("JP1003"), invocation.out());
        assertTrue(invocation.out().contains("JP3001"), invocation.out());
        assertTrue(invocation.out().contains("JP2003"), invocation.out());
        assertTrue(invocation.out().contains("selected " + library), invocation.out());
        assertTrue(invocation.out().contains("from:        " + newer), invocation.out());
        assertTrue(invocation.out().endsWith("2 errors, 1 info, 3 findings\n"), invocation.out());
    }

    @Test
    void keepsBlankLinesAndSurroundingSpaceOutOfTheClasspath() throws IOException {
        Path library = CliFixture.library(workspace);
        Path list = list("\n   " + library + "   \n\n");

        Invocation invocation = check(list);

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("selected " + library), invocation.out());
    }

    /** An empty list is an empty classpath, which is a run the engine can answer. */
    @Test
    void readsAnEmptyListAsAnEmptyClasspath() throws IOException {
        Path list = list("");

        Invocation invocation = check(list);

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("JP1001"), invocation.out());
        assertTrue(invocation.out().endsWith("1 error, 1 finding\n"), invocation.out());
        assertEquals("", invocation.err());
    }

    @Test
    void refusesAListItCannotRead() {
        Path absent = workspace.resolve("absent.txt");

        Invocation invocation = check(absent);

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "This classpath list does not exist or cannot be read: " + absent + "\n", invocation.err());
    }

    /**
     * Every other flag reads a leading {@code @} as text. {@code inspect} names one artifact, so a
     * list is meaningless there and the whole value has to stay the path it looks like.
     */
    @Test
    void keepsALeadingAtSignLiteralOutsideTheClasspath() throws IOException {
        Path list = list(CliFixture.library(workspace) + "\n");

        Invocation invocation = CliFixture.invoke("inspect", AT + list);

        assertEquals(2, invocation.exitCode());
        assertEquals(
                "Cannot inspect this artifact as an archive or a class directory: " + AT + list + "\n",
                invocation.err());
    }

    private Invocation check(Path list) {
        return CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.CLASSPATH,
                AT + list,
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);
    }

    private Path list(String content) throws IOException {
        return Files.writeString(workspace.resolve(LIST), content, StandardCharsets.UTF_8);
    }
}
