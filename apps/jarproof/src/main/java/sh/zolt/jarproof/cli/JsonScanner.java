package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads back the JSON dialect jarproof writes.
 *
 * <p>Only what a baseline file needs is accepted: objects, arrays, strings, and integers. Booleans,
 * {@code null}, and fractional or exponent numbers are rejected rather than silently coerced, so a
 * file that was produced by something else fails loudly at the offset where it stopped making
 * sense instead of half-loading. Objects become {@link LinkedHashMap} and arrays become
 * {@link ArrayList}, so member order survives a read-then-write round trip.
 *
 * <p>All escape sequences JSON defines are understood on the way in, even the short ones this
 * writer never emits, because a baseline is a file people edit by hand.
 */
final class JsonScanner {
    private static final String ESCAPE_CODES = "\"\\/bfnrt";
    private static final String ESCAPE_VALUES = "\"\\/\b\f\n\r\t";
    private static final String MALFORMED = "Malformed JSON at offset ";
    private static final String DIGITS = "0123456789";

    private final String text;
    private int index;

    private JsonScanner(String text) {
        this.text = text;
    }

    /**
     * Parses one complete JSON document.
     *
     * @param text the document
     * @return a {@code Map}, {@code List}, {@code String}, or {@code Integer}
     * @throws IllegalArgumentException when the text is not this dialect of JSON
     */
    static Object parse(String text) {
        JsonScanner scanner = new JsonScanner(text);
        Object value = scanner.readValue();
        scanner.skipWhitespace();
        if (scanner.index != text.length()) {
            throw scanner.malformed();
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        char current = peek();
        if (current == '{') {
            return readObject();
        }
        if (current == '[') {
            return readArray();
        }
        if (current == '"') {
            return readString();
        }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> members = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        while (peek() != '}') {
            if (!members.isEmpty()) {
                expect(',');
                skipWhitespace();
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            members.put(key, readValue());
            skipWhitespace();
        }
        expect('}');
        return members;
    }

    private List<Object> readArray() {
        List<Object> elements = new ArrayList<>();
        expect('[');
        skipWhitespace();
        while (peek() != ']') {
            if (!elements.isEmpty()) {
                expect(',');
            }
            elements.add(readValue());
            skipWhitespace();
        }
        expect(']');
        return elements;
    }

    private String readString() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (peek() != '"') {
            char current = peek();
            index++;
            if (current == '\\') {
                value.append(readEscape());
            } else {
                value.append(current);
            }
        }
        expect('"');
        return value.toString();
    }

    private char readEscape() {
        char code = peek();
        index++;
        if (code == 'u') {
            return readUnicodeEscape();
        }
        int position = ESCAPE_CODES.indexOf(code);
        if (position < 0) {
            throw malformed();
        }
        return ESCAPE_VALUES.charAt(position);
    }

    private char readUnicodeEscape() {
        if (index + 4 > text.length()) {
            throw malformed();
        }
        String digits = text.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(digits, 16);
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    private Integer readNumber() {
        int start = index;
        if (index < text.length() && text.charAt(index) == '-') {
            index++;
        }
        int firstDigit = index;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }
        if (index == firstDigit) {
            throw malformed();
        }
        try {
            return Integer.valueOf(text.substring(start, index));
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    private static boolean isDigit(char character) {
        return DIGITS.indexOf(character) >= 0;
    }

    private void skipWhitespace() {
        while (index < text.length() && text.charAt(index) <= ' ') {
            index++;
        }
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw malformed();
        }
        index++;
    }

    private char peek() {
        if (index >= text.length()) {
            throw malformed();
        }
        return text.charAt(index);
    }

    private IllegalArgumentException malformed() {
        return new IllegalArgumentException(MALFORMED + index);
    }
}
