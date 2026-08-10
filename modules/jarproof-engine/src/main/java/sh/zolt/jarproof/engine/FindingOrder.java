package sh.zolt.jarproof.engine;

import java.util.Comparator;
import sh.zolt.jarproof.api.Finding;

/**
 * The one order findings are reported in.
 *
 * <p>Artifact path text first, then diagnostic code, then subject, then the class entry. Every key is
 * derived from the input rather than from how the analysis happened to run, so shuffled inputs,
 * repeated runs, and different machines all produce the same list. The summary breaks any remaining
 * tie so the order is total rather than merely stable.
 */
final class FindingOrder {
    /** Sorts findings into the canonical report order. */
    static final Comparator<Finding> CANONICAL = Comparator
            .comparing((Finding finding) -> finding.artifact().artifact())
            .thenComparing(finding -> finding.code().value())
            .thenComparing(Finding::subject)
            .thenComparing(finding -> finding.artifact().classEntry().orElse(""))
            .thenComparing(Finding::summary);

    private FindingOrder() {
    }
}
