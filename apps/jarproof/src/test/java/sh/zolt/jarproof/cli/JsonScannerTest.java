package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JsonScannerTest {
    @Test
    void readsObjectsArraysStringsAndIntegers() {
        Object parsed = JsonScanner.parse("{\"a\": [1, -2, \"three\"], \"b\": {}}");

        assertEquals(Map.of("a", List.of(1, -2, "three"), "b", Map.of()), parsed);
    }

    @Test
    void keepsMemberOrder() {
        Object parsed = JsonScanner.parse("{\"z\": 1, \"a\": 2}");

        assertTrue(parsed instanceof Map<?, ?>, String.valueOf(parsed));
        assertEquals(List.of("z", "a"), List.copyOf(((Map<?, ?>) parsed).keySet()));
    }

    @Test
    void ignoresWhitespaceBetweenTokens() {
        assertEquals(Map.of("a", 1), JsonScanner.parse("  {\n  \"a\"  :  1\n}  "));
    }

    /**
     * An empty collection written with something between its brackets is the one place whitespace has
     * to be skipped before the closing bracket is looked for: everywhere else the reader skips it on
     * the way into the value it is about to read. A baseline that has ratcheted all the way down holds
     * exactly this shape, and it is a file people edit by hand.
     */
    @Test
    void readsAnEmptyCollectionWrittenWithSpaceInside() {
        assertEquals(List.of(), JsonScanner.parse("[ ]"));
        assertEquals(Map.of(), JsonScanner.parse("{ }"));
        assertEquals(Map.of("fingerprints", List.of()), JsonScanner.parse("{\"fingerprints\": [\n]}"));
    }

    @Test
    void readsEveryEscapeSequenceJsonDefines() {
        String json = "[\"" + "\\\"" + "\\\\" + "\\/" + "\\b" + "\\f" + "\\n" + "\\r" + "\\t" + "\\u0041" + "\"]";

        Object parsed = JsonScanner.parse(json);

        assertEquals(List.of("\"\\/\b\f\n\r\tA"), parsed);
    }

    @Test
    void refusesTrailingContent() {
        assertTrue(malformed("{} {}").startsWith("Malformed JSON at offset "));
    }

    @Test
    void refusesAnUnterminatedString() {
        assertTrue(malformed("[\"open").startsWith("Malformed JSON at offset "));
    }

    @Test
    void refusesAnUnknownEscape() {
        assertTrue(malformed("[\"\\q\"]").startsWith("Malformed JSON at offset "));
    }

    @Test
    void refusesATruncatedOrInvalidHexEscape() {
        assertTrue(malformed("[\"\\u00\"]").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("[\"\\uzzzz\"]").startsWith("Malformed JSON at offset "));
    }

    /**
     * A hex escape needs four digits behind it, and a document can end before they are all there. The
     * refusal names the offset it stopped at, which is the whole reason the offset is in the message,
     * so these two pin the number rather than the sentence: the escape that runs off the end stops
     * where the digits should have been, while an escape whose digits are the last four characters of
     * the document is read and the string it belongs to is what turns out to be unterminated.
     */
    @Test
    void namesTheOffsetItStoppedAtWhenADocumentEndsInsideAnEscape() {
        assertEquals("Malformed JSON at offset 4", malformed("[\"\\u00"));
        assertEquals("Malformed JSON at offset 8", malformed("[\"\\u0041"));
    }

    @Test
    void refusesValuesThisDialectDoesNotSupport() {
        assertTrue(malformed("true").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("null").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("1.5").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("-").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("99999999999999").startsWith("Malformed JSON at offset "));
    }

    @Test
    void refusesStructuralMistakes() {
        assertTrue(malformed("").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("{\"a\" 1}").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("{\"a\": 1 \"b\": 2}").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("[1 2]").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("{1: 2}").startsWith("Malformed JSON at offset "));
        assertTrue(malformed("[1,").startsWith("Malformed JSON at offset "));
    }

    private static String malformed(String text) {
        return assertThrows(IllegalArgumentException.class, () -> JsonScanner.parse(text)).getMessage();
    }
}
