package sh.zolt.jarproof.api;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one verification run: every finding it produced, in canonical order, and how much
 * the run examined to produce them.
 *
 * <p>The list is defensively copied and always unmodifiable, and an empty list means the analysis
 * ran and found nothing.
 *
 * <p>The two counts are what makes that empty list evidence rather than a claim. "No findings" reads
 * the same whether five thousand classes across forty artifacts all linked or nothing was ever
 * opened, and those are opposite outcomes: the first is the answer a release gate wants and the
 * second is a broken invocation. {@code analyzedClassCount} counts every class the run indexed across
 * every classpath position, nested libraries included, as the target runtime would present them;
 * {@code analyzedArtifactCount} counts the positions themselves.
 *
 * <p>Zero means the count is not stated rather than that nothing was analyzed. A caller assembling a
 * result by hand has no tallies to state and says so through {@link #of(List)}; a report reading a
 * result whose counts are both zero renders its bare form instead of claiming a run examined nothing.
 */
public record VerificationResult(List<Finding> findings, int analyzedClassCount, int analyzedArtifactCount) {
    public VerificationResult {
        Objects.requireNonNull(findings, "A result needs its findings, even when empty");
        if (analyzedClassCount < 0 || analyzedArtifactCount < 0) {
            throw new IllegalArgumentException("A result counts what a run examined, so no count is negative");
        }
        findings = List.copyOf(findings);
    }

    /**
     * Returns a result that states no tallies.
     *
     * @param findings the findings the run produced, in canonical order
     * @return the result, carrying zero for both counts so a report renders its bare form
     */
    public static VerificationResult of(List<Finding> findings) {
        return new VerificationResult(findings, 0, 0);
    }

    /** Returns whether any finding has {@link Severity#ERROR} severity. */
    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
    }
}
