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
 *
 * <p>The index the listing reads is declared by the same pattern, and needs to be: a native image
 * cannot enumerate a classpath folder, so {@code explain} with no code answers out of that resource
 * or not at all.
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
    void declaresTheIndexTheListingReads() throws IOException {
        String resource = "sh/zolt/jarproof/cli/explain/codes.txt";

        assertTrue(Pattern.matches(declaredPattern(), resource), resource + " is not declared");
    }

    /**
     * A resource pattern may hold no colon. native-image reads the first one as the boundary between a
     * module name and the pattern itself, so a non-capturing group -- {@code (?:...)} -- silently becomes
     * a module nobody has plus a pattern that no longer parses, and the image build dies inside the
     * resources feature instead of failing here. Learned by building the image; pinned so it stays
     * learned.
     */
    @Test
    void declaresAPatternTheImageBuilderCanParse() throws IOException {
        String pattern = declaredPattern();

        assertEquals(-1, pattern.indexOf(':'), pattern);
        assertNotNull(Pattern.compile(pattern), pattern);
    }

    @Test
    void declaresNothingBeyondTheExplainTextsAndTheirIndex() throws IOException {
        assertTrue(Pattern.matches(declaredPattern(), "sh/zolt/jarproof/cli/explain/JP1001.txt"));
        assertTrue(!Pattern.matches(declaredPattern(), "sh/zolt/jarproof/cli/explain/JP1001.txtx"));
        assertTrue(!Pattern.matches(declaredPattern(), "sh/zolt/jarproof/cli/explain/codes.txtx"));
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
