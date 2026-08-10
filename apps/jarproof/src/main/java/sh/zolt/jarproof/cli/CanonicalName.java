package sh.zolt.jarproof.cli;

import java.util.Locale;
import java.util.Optional;

/**
 * Translates between a closed vocabulary constant and its canonical spelling on the command line
 * and in machine output.
 *
 * <p>The canonical spelling is the constant's own name lowercased with {@link Locale#ROOT}, so the
 * vocabulary has exactly one source of truth and no report or flag has to repeat the words as
 * string literals. Locale-independent casing is what keeps output byte-identical on a Turkish
 * machine.
 */
final class CanonicalName {
    private CanonicalName() {
    }

    /**
     * Returns the canonical lower-case spelling of a vocabulary constant.
     *
     * @param value any enum constant
     * @return its name, lowercased with {@link Locale#ROOT}
     */
    static String of(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a canonical spelling back to its constant.
     *
     * @param vocabulary the enum type to search
     * @param text text supplied by the caller
     * @param <E> the vocabulary type
     * @return the matching constant, or empty when the text names none
     */
    static <E extends Enum<E>> Optional<E> parse(Class<E> vocabulary, String text) {
        for (E candidate : vocabulary.getEnumConstants()) {
            if (of(candidate).equals(text)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
