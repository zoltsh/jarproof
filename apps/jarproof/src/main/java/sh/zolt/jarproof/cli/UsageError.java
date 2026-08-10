package sh.zolt.jarproof.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * How jarproof answers a command line it cannot parse.
 *
 * <p>Three lines at most: what was wrong, the spelling the parser can suggest instead when it has
 * one, and where the full help is. The default behaviour prints the whole usage text after the
 * message, which buries the one line a reader needs under fifty lines they did not ask for -- and
 * does it on the diagnostic stream, where a pipeline is watching for a report. A refusal is a
 * refusal, so it says what it is and stops.
 *
 * <p>An unrecognised option is named from the dashed tokens alone. The parser reports every argument
 * it could not place, which for {@code --bogus value} is both of them, and calling a consumed value
 * an unknown option sends the reader looking for a flag they never wrote.
 */
final class UsageError {
    private static final String UNKNOWN_OPTION = "Unknown option: ";
    private static final String UNKNOWN_OPTIONS = "Unknown options: ";
    private static final String RUN = "Run '";
    private static final String FOR_DETAILS = " --help' for details.";
    private static final String OPTION_PREFIX = "-";
    private static final char QUOTE = '\'';

    private UsageError() {
    }

    /**
     * Reports one unparseable command line.
     *
     * @param failure what the parser refused, carrying the command it was parsing for
     * @return the process status an invocation error deserves
     */
    static int reported(ParameterException failure) {
        PrintWriter err = failure.getCommandLine().getErr();
        err.println(messageOf(failure));
        UnmatchedArgumentException.printSuggestions(failure, err);
        err.println(RUN + failure.getCommandLine().getCommandSpec().qualifiedName() + FOR_DETAILS);
        err.flush();
        return ExitCode.INVOCATION.status();
    }

    /**
     * The one line that says what was wrong.
     *
     * <p>Everything the parser reports is already a sentence a reader can act on, except an
     * unrecognised option, whose sentence lists the values the option consumed as though they were
     * options too. Those are dropped here rather than in the parser, because the parser is right that
     * it could not place them.
     */
    private static String messageOf(ParameterException failure) {
        if (failure instanceof UnmatchedArgumentException unmatched && unmatched.isUnknownOption()) {
            return named(unmatched.getUnmatched().stream().filter(UsageError::isOption).toList());
        }
        return failure.getMessage();
    }

    private static String named(List<String> options) {
        StringBuilder named =
                new StringBuilder(options.size() == 1 ? UNKNOWN_OPTION : UNKNOWN_OPTIONS);
        for (int index = 0; index < options.size(); index++) {
            if (index > 0) {
                named.append(',').append(' ');
            }
            named.append(QUOTE).append(options.get(index)).append(QUOTE);
        }
        return named.toString();
    }

    private static boolean isOption(String token) {
        return token.startsWith(OPTION_PREFIX);
    }
}
