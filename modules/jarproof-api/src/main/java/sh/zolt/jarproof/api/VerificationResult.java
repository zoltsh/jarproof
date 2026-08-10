package sh.zolt.jarproof.api;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one verification run: every finding it produced, in canonical order.
 *
 * <p>The list is defensively copied and always unmodifiable, and an empty list means the analysis
 * ran and found nothing.
 */
public record VerificationResult(List<Finding> findings) {
    public VerificationResult {
        Objects.requireNonNull(findings, "A result needs its findings, even when empty");
        findings = List.copyOf(findings);
    }

    /** Returns whether any finding has {@link Severity#ERROR} severity. */
    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
    }
}
