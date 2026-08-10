package sh.zolt.jarproof.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Accumulates canonical JSON text.
 *
 * <p>The canonical form is fixed so that the same analysis rendered twice, on two machines or by
 * the JVM and the native binary, produces byte-identical output: LF line endings, two-space
 * indentation, one member or element per line, an empty object or array on a single line, and keys
 * in exactly the order the caller writes them. Sorting is the producer's job, not this writer's.
 *
 * <p>Only the escapes JSON requires are emitted: the quote, the backslash, and a six-character
 * hexadecimal escape for characters below U+0020. Every other character is written through as raw
 * UTF-8 text, which keeps emoji and the line separators U+2028 and U+2029 byte-for-byte intact.
 */
final class JsonText {
    private static final String INDENT = "  ";
    private static final String ESCAPED_QUOTE = "\\\"";
    private static final String ESCAPED_BACKSLASH = "\\\\";
    private static final String CONTROL_ESCAPE = "\\u%04X";
    private static final int FIRST_PRINTABLE = 0x20;

    private final StringBuilder out = new StringBuilder();
    private final Deque<Integer> filled = new ArrayDeque<>();
    private boolean namePending;

    /** Opens an object; every following {@link #name(String)} belongs to it. */
    JsonText beginObject() {
        return open('{');
    }

    /** Closes the innermost object. */
    JsonText endObject() {
        return close('}');
    }

    /** Opens an array; every following value becomes an element of it. */
    JsonText beginArray() {
        return open('[');
    }

    /** Closes the innermost array. */
    JsonText endArray() {
        return close(']');
    }

    /**
     * Writes a member key. The next value written becomes its value.
     *
     * @param key the member name
     * @return this writer
     */
    JsonText name(String key) {
        beginEntry();
        appendString(key);
        out.append(':').append(' ');
        namePending = true;
        return this;
    }

    /**
     * Writes a string, as a member value or an array element.
     *
     * @param value the text to escape and emit
     * @return this writer
     */
    JsonText value(String value) {
        beginValue();
        appendString(value);
        return this;
    }

    /**
     * Writes an integer, as a member value or an array element.
     *
     * @param value the number to emit
     * @return this writer
     */
    JsonText value(int value) {
        beginValue();
        out.append(value);
        return this;
    }

    /** Returns the finished document, terminated by a single LF. */
    String document() {
        return out.toString() + '\n';
    }

    private JsonText open(char bracket) {
        beginValue();
        out.append(bracket);
        filled.push(0);
        return this;
    }

    private JsonText close(char bracket) {
        int entries = filled.pop();
        if (entries > 0) {
            out.append('\n').append(INDENT.repeat(filled.size()));
        }
        out.append(bracket);
        return this;
    }

    private void beginValue() {
        if (namePending) {
            namePending = false;
        } else if (!filled.isEmpty()) {
            beginEntry();
        }
    }

    private void beginEntry() {
        int entries = filled.pop();
        if (entries > 0) {
            out.append(',');
        }
        filled.push(entries + 1);
        out.append('\n').append(INDENT.repeat(filled.size()));
    }

    private void appendString(String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            appendCharacter(value.charAt(index));
        }
        out.append('"');
    }

    private void appendCharacter(char character) {
        if (character == '"') {
            out.append(ESCAPED_QUOTE);
        } else if (character == '\\') {
            out.append(ESCAPED_BACKSLASH);
        } else if (character < FIRST_PRINTABLE) {
            out.append(String.format(Locale.ROOT, CONTROL_ESCAPE, (int) character));
        } else {
            out.append(character);
        }
    }
}
