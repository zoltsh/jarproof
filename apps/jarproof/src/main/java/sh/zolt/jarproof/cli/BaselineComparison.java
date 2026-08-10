package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * What a fresh run looks like once an accepted baseline is applied.
 *
 * <p>Three numbers make a baseline usable as a ratchet: the findings that are new and therefore
 * worth failing on, how many the baseline suppressed, and which accepted fingerprints no longer
 * occur. The last group is the important one -- a baseline nobody prunes becomes permanent
 * permission, so stale entries are reported and can be removed as the code improves.
 *
 * <p>New findings keep the order the engine produced them in, and stale fingerprints keep the
 * sorted order the baseline stores.
 */
record BaselineComparison(List<Finding> newFindings, int suppressed, List<String> stale) {
    BaselineComparison {
        Objects.requireNonNull(newFindings, "A comparison needs its new findings, even when none");
        Objects.requireNonNull(stale, "A comparison needs its stale fingerprints, even when none");
        if (suppressed < 0) {
            throw new IllegalArgumentException("A suppressed count cannot be negative: " + suppressed);
        }
        newFindings = List.copyOf(newFindings);
        stale = List.copyOf(stale);
    }

    /**
     * Partitions a fresh run against an accepted baseline.
     *
     * @param result the findings the fresh run produced
     * @param baseline the accepted fingerprints
     * @param root the root the fresh run's fingerprints are measured from
     * @return the new findings, the suppressed count, and the stale fingerprints
     */
    static BaselineComparison against(VerificationResult result, BaselineDocument baseline, PathRoot root) {
        Set<String> accepted = Set.copyOf(baseline.fingerprints());
        Set<String> occurring = new LinkedHashSet<>();
        List<Finding> reported = new ArrayList<>();
        for (Finding finding : result.findings()) {
            String fingerprint = BaselineFingerprint.of(root, finding);
            occurring.add(fingerprint);
            if (!accepted.contains(fingerprint)) {
                reported.add(finding);
            }
        }
        List<String> stale = baseline.fingerprints().stream()
                .filter(fingerprint -> !occurring.contains(fingerprint))
                .toList();
        return new BaselineComparison(reported, result.findings().size() - reported.size(), stale);
    }
}
