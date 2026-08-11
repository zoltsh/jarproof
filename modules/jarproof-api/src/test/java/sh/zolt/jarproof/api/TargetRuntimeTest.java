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

    /**
     * Release 8 is the oldest release jarproof analyses, so it is a supported release and not the
     * first refused one. Pinning the refusal alone would leave the boundary free to move a release up
     * and reject every Java 8 application with a message naming Java 8 as supported.
     */
    @Test
    void acceptsTheOldestReleaseItSupports() {
        assertEquals(8, TargetRuntime.of(8).javaRelease());
    }

    @Test
    void rejectsMissingPreviewPolicy() {
        assertThrows(NullPointerException.class, () -> new TargetRuntime(21, null));
    }
}
