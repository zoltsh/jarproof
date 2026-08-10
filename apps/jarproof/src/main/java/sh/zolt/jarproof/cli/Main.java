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
 *
 * <p>Three parser decisions are made here, once, for the whole command hierarchy. Vocabulary
 * conversion is registered so a bad flag value is reported against its own flag. A command line the
 * parser cannot read is answered by {@link UsageError} instead of by a usage dump. And argument-file
 * expansion is switched off: jarproof reads {@code @file} lists for {@code --classpath} through
 * {@link ClasspathEntries} and nowhere else, so a leading {@code @} anywhere else stays the literal
 * text the caller wrote.
 *
 * <p>That last decision is applied to every command in the tree rather than to the root alone. The
 * setting is per-command state -- {@code setExpandAtFiles} writes only the parser spec of the command
 * it is called on, and adding a subcommand copies nothing -- so the root's answer is the whole
 * hierarchy's answer only because today's parser expands once, over the original argument array,
 * before any subcommand is reached. Saying it everywhere it can be asked costs one walk and removes
 * the dependency on that internal detail: were a subcommand ever parsed on its own, a multi-line
 * classpath list would otherwise arrive as an unmatched argument instead of as a classpath.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = execute(writerFor(System.out), writerFor(System.err), args);
        System.exit(exitCode);
    }

    static int execute(PrintWriter out, PrintWriter err, String... args) {
        return literalAtSigns(FlagVocabulary.on(new CommandLine(new RootCommand())))
                .setParameterExceptionHandler((failure, arguments) -> UsageError.reported(failure))
                .setOut(out)
                .setErr(err)
                .execute(args);
    }

    /** Switches off argument-file expansion on one command and on every command beneath it. */
    private static CommandLine literalAtSigns(CommandLine command) {
        command.setExpandAtFiles(false);
        command.getSubcommands().values().forEach(Main::literalAtSigns);
        return command;
    }

    private static PrintWriter writerFor(OutputStream stream) {
        return new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), true);
    }
}
