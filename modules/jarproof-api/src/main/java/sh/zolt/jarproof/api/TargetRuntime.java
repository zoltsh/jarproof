package sh.zolt.jarproof.api;

import java.util.Objects;

/** Explicit Java runtime assumptions used by an analysis. */
public record TargetRuntime(int javaRelease, PreviewMode previewMode) {
    private static final int MINIMUM_SUPPORTED_RELEASE = 8;

    public TargetRuntime {
        if (javaRelease < MINIMUM_SUPPORTED_RELEASE) {
            throw new IllegalArgumentException("Target Java release must be 8 or newer: " + javaRelease);
        }
        Objects.requireNonNull(previewMode, "previewMode");
    }

    /** Creates a target runtime with preview features disabled. */
    public static TargetRuntime of(int javaRelease) {
        return new TargetRuntime(javaRelease, PreviewMode.DISABLED);
    }

    /** Returns the same Java release with the requested preview policy. */
    public TargetRuntime withPreviewMode(PreviewMode mode) {
        return previewMode == mode ? this : new TargetRuntime(javaRelease, mode);
    }
}
