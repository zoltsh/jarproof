package sh.zolt.jarproof.cli;

import java.util.Arrays;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.TypeConversionException;
import sh.zolt.jarproof.api.Scope;

/**
 * Teaches the command line to read every closed vocabulary a flag accepts.
 *
 * <p>Each vocabulary is spelled the one canonical way {@link CanonicalName} defines, in lower case,
 * and nothing else is accepted -- not an upper-case variant, not an abbreviation -- because a flag
 * value that means one thing here and another on a colleague's machine is worse than a rejected
 * invocation. Registering the conversion rather than parsing strings inside each command is what
 * lets a bad value be reported against the flag that carried it, with the choices listed, without
 * any command repeating the flag's own name.
 */
final class FlagVocabulary {
    private static final String CHOICE_SEPARATOR = "|";
    private static final String NOT_A_CHOICE = " is not one of: ";

    private FlagVocabulary() {
    }

    /**
     * Registers a converter for every vocabulary the commands accept.
     *
     * @param command the command line to teach, with its subcommands already attached
     * @return the same command line
     */
    static CommandLine on(CommandLine command) {
        return command
                .registerConverter(Scope.class, text -> constant(Scope.class, text))
                .registerConverter(ReportFormat.class, text -> constant(ReportFormat.class, text))
                .registerConverter(FailureThreshold.class, text -> constant(FailureThreshold.class, text))
                .registerConverter(InspectFormat.class, text -> constant(InspectFormat.class, text));
    }

    private static <E extends Enum<E>> String choices(Class<E> vocabulary) {
        return Arrays.stream(vocabulary.getEnumConstants())
                .map(CanonicalName::of)
                .collect(Collectors.joining(CHOICE_SEPARATOR));
    }

    private static <E extends Enum<E>> E constant(Class<E> vocabulary, String text) {
        return CanonicalName.parse(vocabulary, text)
                .orElseThrow(() -> new TypeConversionException(text + NOT_A_CHOICE + choices(vocabulary)));
    }
}
