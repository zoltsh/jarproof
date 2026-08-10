package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The explain texts are classpath resources, and native-image includes no resource unless it is
 * declared. Without the declaration the native binary would fail every {@code explain} invocation
 * while the JVM build stayed green, so the declaration is checked here rather than discovered at
 * release time.
 */
final class NativeResourceConfigTest {
    private static final String CONFIG = "/META-INF/native-image/sh.zolt/jarproof-cli/resource-config.json";

    @Test
    void declaresEveryExplainTextForTheNativeImage() throws IOException {
        String pattern = declaredPattern();

        for (String code : List.of("JP1001", "JP2006", "JP3004", "JP4005")) {
            String resource = "sh/zolt/jarproof/cli/explain/" + code + ".txt";
            assertTrue(Pattern.matches(pattern, resource), resource + " is not matched by " + pattern);
        }
    }

    @Test
    void declaresNothingBeyondTheExplainTexts() throws IOException {
        assertTrue(Pattern.matches(declaredPattern(), "sh/zolt/jarproof/cli/explain/JP1001.txt"));
        assertTrue(!Pattern.matches(declaredPattern(), "sh/zolt/jarproof/cli/explain/JP1001.txtx"));
        assertTrue(!Pattern.matches(declaredPattern(), "sh/zolt/jarproof/cli/explain/notes.txt"));
    }

    private static String declaredPattern() throws IOException {
        try (InputStream stream = NativeResourceConfigTest.class.getResourceAsStream(CONFIG)) {
            assertNotNull(stream, CONFIG);
            Object parsed = JsonScanner.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            Object resources = ((java.util.Map<?, ?>) parsed).get("resources");
            Object includes = ((java.util.Map<?, ?>) resources).get("includes");
            List<?> entries = (List<?>) includes;
            assertEquals(1, entries.size(), String.valueOf(entries));
            return (String) ((java.util.Map<?, ?>) entries.get(0)).get("pattern");
        }
    }
}
