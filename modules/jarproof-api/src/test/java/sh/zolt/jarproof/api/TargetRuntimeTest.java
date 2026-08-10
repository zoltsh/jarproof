package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class TargetRuntimeTest {
    @Test
    void defaultsPreviewFeaturesOff() {
        TargetRuntime runtime = TargetRuntime.of(17);

        assertEquals(17, runtime.javaRelease());
        assertEquals(PreviewMode.DISABLED, runtime.previewMode());
    }

    @Test
    void enablesPreviewWithoutChangingRelease() {
        TargetRuntime runtime = TargetRuntime.of(21).withPreviewMode(PreviewMode.ENABLED);

        assertEquals(21, runtime.javaRelease());
        assertEquals(PreviewMode.ENABLED, runtime.previewMode());
        assertSame(runtime, runtime.withPreviewMode(PreviewMode.ENABLED));
    }

    @Test
    void rejectsUnsupportedReleases() {
        assertThrows(IllegalArgumentException.class, () -> TargetRuntime.of(7));
    }

    @Test
    void rejectsMissingPreviewPolicy() {
        assertThrows(NullPointerException.class, () -> new TargetRuntime(21, null));
    }
}
