package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

final class ClosedVocabularyTest {
    @Test
    void scopeVocabularyIsDeliberatelySmall() {
        assertArrayEquals(
                new Scope[] {Scope.APPLICATION, Scope.ALL, Scope.REACHABLE},
                Scope.values());
    }

    @Test
    void previewModeAvoidsAmbiguousBooleanState() {
        assertArrayEquals(
                new PreviewMode[] {PreviewMode.DISABLED, PreviewMode.ENABLED},
                PreviewMode.values());
    }

    @Test
    void severityVocabularyIsStableAndOrdered() {
        assertArrayEquals(
                new Severity[] {Severity.INFO, Severity.WARNING, Severity.ERROR},
                Severity.values());
    }

}
