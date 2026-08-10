package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class EngineSafetyArchitectureTest {
    private static final String ENGINE = "modules/jarproof-engine";
    private static final Set<String> FORBIDDEN_ENGINE_TOKENS = Set.of(
            "Class.forName(",
            ".loadClass(",
            "ClassLoader",
            "java.lang.reflect",
            "java.net.",
            "ProcessBuilder",
            "Runtime.getRuntime(",
            "System.setProperty(");

    @Test
    void engineHasOnlyItsDeliberateDependencies() {
        assertEquals(
                Set.of("sh.zolt:jarproof-api", "org.ow2.asm:asm"),
                RepositoryLayout.dependencyCoordinates(ENGINE));
    }

    @Test
    void engineNeverLoadsOrExecutesAnalyzedCode() {
        for (Path source : RepositoryLayout.productionJavaFiles(ENGINE)) {
            String content = RepositoryLayout.text(source);
            for (String token : FORBIDDEN_ENGINE_TOKENS) {
                assertFalse(content.contains(token), RepositoryLayout.relative(source) + " contains " + token);
            }
        }
    }
}
