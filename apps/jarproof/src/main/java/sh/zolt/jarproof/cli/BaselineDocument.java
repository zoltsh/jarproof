package sh.zolt.jarproof.cli;

import java.util.List;
import java.util.Objects;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * A recorded set of accepted findings, plus what they were accepted against.
 *
 * <p>A brownfield project cannot fix every existing finding before it starts gating, so it records
 * them once and gates on what is new. That only works if the file says what the acceptance means:
 * the same fingerprints measured against a different Java release, scope, or runtime profile are
 * not the same judgement, so those three travel with the list and let a reader tell an out-of-date
 * baseline from a valid one.
 *
 * <p>Fingerprints are normalised to a sorted, duplicate-free list, so two runs that accept the same
 * findings write byte-identical files no matter what order the findings arrived in.
 */
record BaselineDocument(
        int targetJava,
        PreviewMode preview,
        Scope scope,
        String profile,
        List<String> fingerprints) {
    BaselineDocument {
        Objects.requireNonNull(preview, "A baseline needs the preview policy it was recorded under");
        Objects.requireNonNull(scope, "A baseline needs the scope it was recorded under");
        Objects.requireNonNull(profile, "A baseline needs a runtime profile identity");
        Objects.requireNonNull(fingerprints, "A baseline needs its fingerprints, even when none");
        if (profile.isBlank()) {
            throw new IllegalArgumentException("A runtime profile identity must name the symbol source");
        }
        fingerprints = fingerprints.stream().distinct().sorted().toList();
    }

    /**
     * Records every finding of a completed run as accepted.
     *
     * @param request the request the run answered
     * @param profile identity of the runtime symbol profile the run used
     * @param result the findings to accept
     * @return the baseline to write
     */
    static BaselineDocument of(VerificationRequest request, String profile, VerificationResult result) {
        return new BaselineDocument(
                request.targetRuntime().javaRelease(),
                request.targetRuntime().previewMode(),
                request.scope(),
                profile,
                result.findings().stream().map(BaselineFingerprint::of).toList());
    }

    /**
     * Returns this baseline carrying a different set of accepted fingerprints.
     *
     * <p>Every envelope field travels unchanged. Rewriting a file therefore records the acceptance it
     * already held over a shorter list, instead of quietly rebranding it as measured against the
     * release, scope, or profile of whichever run happened to do the rewriting.
     *
     * @param fingerprints the accepted fingerprints the rewritten file holds
     * @return the baseline to write
     */
    BaselineDocument withFingerprints(List<String> fingerprints) {
        return new BaselineDocument(targetJava, preview, scope, profile, fingerprints);
    }
}
