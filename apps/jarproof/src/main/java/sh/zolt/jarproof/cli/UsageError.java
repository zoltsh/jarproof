package sh.zolt.jarproof.cli;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * How jarproof answers a command line it cannot parse.
 *
 * <p>Three lines at most: what was wrong, the spelling it probably meant when there is one, and
 * where the full help is. The default behaviour prints the whole usage text after the message, which
 * buries the one line a reader needs under fifty lines they did not ask for -- and does it on the
 * diagnostic stream, where a pipeline is watching for a report. A refusal is a refusal, so it says
 * what it is and stops.
 *
 * <p>An unrecognised option is named from the dashed tokens alone. The parser reports every argument
 * it could not place, which for {@code --bogus value} is both of them, and calling a consumed value
 * an unknown option sends the reader looking for a flag they never wrote.
 *
 * <p>The suggestion line is the parser's whenever the parser has one, and jarproof's own otherwise.
 * The parser matches by leading characters, which answers a truncation -- {@code --applicaton} finds
 * {@code --application} -- and says nothing at all about the commonest typo there is: two characters
 * the wrong way round. {@code --fromat} shares no prefix with {@code --format}, so the reader was
 * being told their flag was unknown and left to find the difference themselves. So a spelling within
 * two edits ({@link SpellingDistance}) of a declared option name is offered instead, in the parser's
 * own wording, because a reader should not be able to tell which of the two answered them.
 */
final class UsageError {
    private static final String UNKNOWN_OPTION = "Unknown option: ";
    private static final String UNKNOWN_OPTIONS = "Unknown options: ";
    private static final String POSSIBLE_SOLUTIONS = "Possible solutions: ";
    private static final String RUN = "Run '";
    private static final String FOR_DETAILS = " --help' for details.";
    private static final String OPTION_PREFIX = "-";
    private static final char QUOTE = '\'';

    /** The most edits a spelling may be from an option name and still be worth offering. */
    private static final int NEAR_ENOUGH = 2;

    /** How many spellings to offer. Past a couple, a list stops being a suggestion. */
    private static final int AT_MOST = 2;

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
        if (!UnmatchedArgumentException.printSuggestions(failure, err)) {
            suggestion(failure).ifPresent(err::println);
        }
        err.println(RUN + failure.getCommandLine().getCommandSpec().qualifiedName() + FOR_DETAILS);
        err.flush();
        return ExitCode.INVOCATION.status();
    }

    /**
     * The spellings a misspelt option was probably reaching for.
     *
     * <p>Only an unrecognised option is answered. Everything else the parser refuses is either a
     * value it could not convert, which already names its own flag, or an argument that is not an
     * option at all, where offering option names would send the reader further from the mistake.
     *
     * @param failure what the parser refused
     * @return the complete suggestion line, or nothing when no declared name is near enough
     */
    private static Optional<String> suggestion(ParameterException failure) {
        if (!(failure instanceof UnmatchedArgumentException unmatched) || !unmatched.isUnknownOption()) {
            return Optional.empty();
        }
        Optional<String> written = unmatched.getUnmatched().stream().filter(UsageError::isOption).findFirst();
        List<String> nearest = written.map(text -> nearest(text, unmatched)).orElse(List.of());
        return nearest.isEmpty() ? Optional.empty() : Optional.of(POSSIBLE_SOLUTIONS + listed(nearest));
    }

    /**
     * The declared option names closest to what was written, nearest first and then alphabetically so
     * the same command line is always answered with the same list.
     */
    private static List<String> nearest(String written, UnmatchedArgumentException unmatched) {
        return unmatched.getCommandLine().getCommandSpec().optionsMap().keySet().stream()
                .filter(name -> SpellingDistance.between(written, name) <= NEAR_ENOUGH)
                .sorted(Comparator.comparingInt((String name) -> SpellingDistance.between(written, name))
                        .thenComparing(Comparator.<String>naturalOrder()))
                .limit(AT_MOST)
                .toList();
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

    /** Joins spellings the way the parser joins its own, which is what keeps the two lines alike. */
    private static String listed(List<String> names) {
        StringBuilder listed = new StringBuilder();
        for (String name : names) {
            if (!listed.isEmpty()) {
                listed.append(',').append(' ');
            }
            listed.append(name);
        }
        return listed.toString();
    }

    private static boolean isOption(String token) {
        return token.startsWith(OPTION_PREFIX);
    }
}
