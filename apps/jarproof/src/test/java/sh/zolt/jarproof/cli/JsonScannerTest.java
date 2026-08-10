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
