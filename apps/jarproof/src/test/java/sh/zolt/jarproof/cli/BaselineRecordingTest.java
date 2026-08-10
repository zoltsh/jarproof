package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

/**
 * What recording a baseline says for itself, and what it writes into the file.
 *
 * <p>The file is the entire output of the command, so a run that recorded a hundred findings and a run
 * that recorded none are otherwise the same silence -- and the difference between them is exactly what
 * the person running it needs to see. The recorded text is the other half: a fingerprint measured from
 * the root travels between machines, and a fingerprint carrying whatever the caller's shell happened to
 * spell does not.
 */
final class BaselineRecordingTest {
    private static final String BASELINE = "baseline";
    private static final String OUT = "--out";
    private static final String RECORDED_ARTIFACT = "\"JP1003|app.jar|";

    @TempDir
    Path workspace;

    /** Recording writes only a file, so it says how much went into it. */
    @Test
    void saysHowManyFindingsItRecorded() {
        Invocation invocation = record(CliFixture.brokenApplication(workspace));

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("", invocation.out());
        assertEquals("wrote 1 accepted finding to " + baselineFile() + "\n", invocation.err());
    }

    @Test
    void saysSoWhenItRecordedNothingAtAll() {
        Invocation invocation = record(CliFixture.library(workspace));

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("wrote 0 accepted findings to " + baselineFile() + "\n", invocation.err());
    }

    /** {@code --output} is what the other commands call this flag, so it works here as well. */
    @Test
    void recordsIntoTheFileNamedByEitherSpellingOfTheFlag() throws IOException {
        Invocation invocation = CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                "--output",
                baselineFile().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(recorded().contains("JP1003"), recorded());
    }

    /**
     * An absolute recording measured from a root records the root-relative text, which is the same text
     * a run naming those files relatively from that root records -- and the reason a committed baseline
     * survives the move from one machine's build directory to another's.
     */
    @Test
    void recordsTheRootRelativeTextWhateverSpellingItWasGiven() throws IOException {
        Invocation invocation = CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                "--path-root",
                workspace.toString(),
                OUT,
                baselineFile().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(recorded().contains(RECORDED_ARTIFACT), recorded());
    }

    private Invocation record(Path application) {
        return CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                OUT,
                baselineFile().toString());
    }

    private String recorded() {
        try {
            return Files.readString(baselineFile());
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private Path baselineFile() {
        return workspace.resolve("jarproof-baseline.json");
    }
}
