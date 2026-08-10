package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JsonTextTest {
    @Test
    void escapesQuotesAndBackslashes() {
        String document = new JsonText()
                .beginObject()
                .name("quote")
                .value("say \"hi\"")
                .name("path")
                .value("C:\\tools\\bin")
                .endObject()
                .document();

        assertTrue(document.contains("\"quote\": \"say \\\"hi\\\"\""), document);
        assertTrue(document.contains("\"path\": \"C:\\\\tools\\\\bin\""), document);
    }

    @Test
    void escapesControlCharactersAsSixCharacterHex() {
        String value = "a\nb\tc" + (char) 0 + 'd';

        String document = new JsonText().beginArray().value(value).endArray().document();

        assertTrue(document.contains("\"a\\u000Ab\\u0009c\\u0000d\""), document);
    }

    @Test
    void passesEmojiAndLineSeparatorsThroughAsRawText() {
        String value = "ship " + Character.toString(0x1F680) + (char) 0x2028 + (char) 0x2029;

        String document = new JsonText().beginArray().value(value).endArray().document();

        assertTrue(document.contains(value), document);
        assertEquals(-1, document.indexOf('\\'), document);
    }

    @Test
    void rendersEmptyContainersOnOneLine() {
        String document = new JsonText()
                .beginObject()
                .name("list")
                .beginArray()
                .endArray()
                .name("map")
                .beginObject()
                .endObject()
                .endObject()
                .document();

        assertEquals("""
                {
                  "list": [],
                  "map": {}
                }
                """, document);
    }

    @Test
    void indentsTwoSpacesPerLevelAndKeepsWrittenOrder() {
        String document = new JsonText()
                .beginObject()
                .name("second")
                .value(2)
                .name("first")
                .beginArray()
                .value("deep")
                .beginObject()
                .name("deeper")
                .value(-7)
                .endObject()
                .endArray()
                .endObject()
                .document();

        assertEquals("""
                {
                  "second": 2,
                  "first": [
                    "deep",
                    {
                      "deeper": -7
                    }
                  ]
                }
                """, document);
    }

    @Test
    void terminatesTheDocumentWithExactlyOneLineFeed() {
        String document = new JsonText().beginArray().endArray().document();

        assertEquals("[]\n", document);
    }
}
