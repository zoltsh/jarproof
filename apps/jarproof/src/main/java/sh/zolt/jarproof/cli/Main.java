package sh.zolt.jarproof.cli;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine;

/**
 * Jarproof process entry point.
 *
 * <p>Both streams are wrapped in UTF-8 writers rather than left to the platform default, because
 * canonical output is a byte-level contract: a report containing a non-ASCII symbol name has to
 * come out the same on every machine.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = execute(writerFor(System.out), writerFor(System.err), args);
        System.exit(exitCode);
    }

    static int execute(PrintWriter out, PrintWriter err, String... args) {
        return new CommandLine(new RootCommand()).setOut(out).setErr(err).execute(args);
    }

    private static PrintWriter writerFor(OutputStream stream) {
        return new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), true);
    }
}
