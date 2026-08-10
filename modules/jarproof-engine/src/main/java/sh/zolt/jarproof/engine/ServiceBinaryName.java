package sh.zolt.jarproof.engine;

/**
 * The one grammar Jarproof accepts for a name written in a service configuration file.
 *
 * <p>{@code ServiceLoader} treats the file name as the service's binary name and every surviving
 * line as a provider's binary name, so the only text worth resolving is text that could name a
 * class at all: one or more Java identifiers joined by single dots, with no empty segment and no
 * leading or trailing dot. A dollar sign is an ordinary identifier character, which is what lets a
 * nested provider such as {@code com.acme.Outer$Inner} be declared, and identifiers are measured by
 * code point so a name outside the basic multilingual plane is judged on its own merits.
 *
 * <p>This grammar is deliberately stricter than the runtime's own line check, which tolerates an
 * empty segment such as {@code com..acme.Codec}. Such a name can never name a class, so the runtime
 * merely fails a moment later while resolving it; naming it malformed here reports the same
 * {@code ServiceConfigurationError} against the text that actually needs editing.
 */
final class ServiceBinaryName {
    private static final char SEGMENT_SEPARATOR = '.';
    private static final char INTERNAL_SEPARATOR = '/';

    private ServiceBinaryName() {
    }

    /**
     * Returns whether this text is a binary class name a runtime could ask for.
     *
     * @param text the name exactly as the configuration file wrote it
     * @return whether every segment is a Java identifier
     */
    static boolean isLegal(String text) {
        int index = 0;
        int segmentLength = 0;
        while (index < text.length()) {
            int character = text.codePointAt(index);
            if (!isLegalAt(character, segmentLength)) {
                return false;
            }
            segmentLength = character == SEGMENT_SEPARATOR ? 0 : segmentLength + 1;
            index += Character.charCount(character);
        }
        return segmentLength > 0;
    }

    /**
     * Converts a binary class name into the internal name a class is looked up by.
     *
     * @param binaryName dot-separated binary name, such as {@code com.acme.orders.Codec}
     * @return the internal name, such as {@code com/acme/orders/Codec}
     */
    static String internalName(String binaryName) {
        return binaryName.replace(SEGMENT_SEPARATOR, INTERNAL_SEPARATOR);
    }

    private static boolean isLegalAt(int character, int segmentLength) {
        if (character == SEGMENT_SEPARATOR) {
            return segmentLength > 0;
        }
        return segmentLength == 0
                ? Character.isJavaIdentifierStart(character)
                : Character.isJavaIdentifierPart(character);
    }
}
