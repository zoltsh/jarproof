package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a program on a real JVM and reports what happened.
 *
 * <p>This is the half of the harness that jarproof cannot influence: the launcher the tests run is
 * the same {@code java} binary that runs this test JVM, told nothing but a classpath and a main
 * class, so the throwable it reports is the JVM's own verdict rather than a model of one. A
 * prediction is only worth the name if something independent can contradict it.
 *
 * <p>Every process starts in the workspace root, which is where a developer runs the tool from and
 * therefore what the relative paths in a report and a baseline are measured against.
 *
 * <p>Both streams are redirected to files instead of pipes, because a pipe that nobody drains
 * deadlocks as soon as a stack trace outgrows its buffer, and every interesting run here ends in a
 * stack trace. A run that outlives the timeout is destroyed and reported with whatever it had
 * written, so a hang is a legible failure rather than a build that never ends.
 */
final class CorpusProcess {
    private static final long TIMEOUT_SECONDS = 120;
    private static final String JAVA_HOME = "java.home";
    private static final String BIN = "bin";
    private static final String JAVA = "java";
    private static final String CLASSPATH = "-cp";
    private static final String JAR = "-jar";
    private static final String STDOUT = "stdout.txt";
    private static final String STDERR = "stderr.txt";
    private static final String RUN_PREFIX = "run";
    private static final String TIMED_OUT = "The fixture JVM did not finish within ";

    private CorpusProcess() {
    }

    /**
     * Runs one main class against one classpath.
     *
     * @param captureDirectory an existing directory the run's output streams are written under
     * @param classpath the {@code -cp} value, already joined with the platform separator
     * @param mainClass the binary name of the class to launch
     * @return the exit status and both streams
     */
    static Outcome launch(Path captureDirectory, String classpath, String mainClass) {
        return execute(captureDirectory, List.of(java(), CLASSPATH, classpath, mainClass));
    }

    /**
     * Runs the packaged command line from the workspace root.
     *
     * <p>Everything else in this harness calls the entry point in process, which is faster and reads
     * better. This one cannot: the working directory is part of what it verifies. A baseline records
     * the caller's own path text, so proving that relative text round-trips means being a process
     * whose working directory is the root those paths are relative to.
     *
     * @param captureDirectory an existing directory the run's output streams are written under
     * @param arguments the command line, subcommand first
     * @return the exit status and both streams
     */
    static Outcome jarproof(Path captureDirectory, List<String> arguments) {
        List<String> command = new ArrayList<>(List.of(java(), JAR, FixtureCorpus.commandLineJar().toString()));
        command.addAll(arguments);
        return execute(captureDirectory, command);
    }

    private static Outcome execute(Path captureDirectory, List<String> command) {
        try {
            Path captures = Files.createTempDirectory(captureDirectory, RUN_PREFIX);
            Path out = captures.resolve(STDOUT);
            Path err = captures.resolve(STDERR);
            Process process = new ProcessBuilder(command)
                    .directory(FixtureCorpus.workspaceRoot().toFile())
                    .redirectOutput(out.toFile())
                    .redirectError(err.toFile())
                    .start();
            return finish(process, out, err, String.join(" ", command));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static Outcome finish(Process process, Path out, Path err, String command)
            throws InterruptedException {
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            throw new IllegalStateException(
                    TIMED_OUT + TIMEOUT_SECONDS + "s: " + command + "\n" + text(out) + text(err));
        }
        return new Outcome(process.exitValue(), text(out), text(err));
    }

    private static String java() {
        return Path.of(System.getProperty(JAVA_HOME)).resolve(BIN).resolve(JAVA).toString();
    }

    private static String text(Path captured) {
        try {
            return Files.readString(captured).replace("\r\n", "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** One complete JVM run: what it returned, and what it wrote to each stream. */
    record Outcome(int exitCode, String out, String err) {
    }
}
