package sh.zolt.jarproof.api;

import java.util.Objects;

/** One concrete fact Jarproof observed while producing a finding. */
public record Evidence(String detail) {
    public Evidence {
        Objects.requireNonNull(detail, "A piece of evidence needs a detail");
        if (detail.isBlank()) {
            throw new IllegalArgumentException("Evidence must state one fact that was observed");
        }
    }
}
