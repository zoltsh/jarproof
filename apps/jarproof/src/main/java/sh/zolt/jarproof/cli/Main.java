package sh.zolt.jarproof.cli;

import java.io.PrintWriter;
import picocli.CommandLine;

/** Jarproof process entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = execute(new PrintWriter(System.out, true), new PrintWriter(System.err, true), args);
        System.exit(exitCode);
    }

    static int execute(PrintWriter out, PrintWriter err, String... args) {
        return new CommandLine(new RootCommand()).setOut(out).setErr(err).execute(args);
    }
}
