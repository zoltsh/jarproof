package sh.zolt.jarproof.api;

import java.util.Objects;

/** One imperative next step that resolves a finding. */
public record Remediation(String action) {
    public Remediation {
        Objects.requireNonNull(action, "A remediation needs an action");
        if (action.isBlank()) {
            throw new IllegalArgumentException("A remediation must name one step the caller can take");
        }
    }
}
