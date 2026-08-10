package sh.zolt.jarproof.cli;

import java.util.Optional;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The {@code --fail-on} vocabulary: how severe a finding must be before jarproof fails the build.
 *
 * <p>The default is {@link #ERROR}, because only application-origin breakage is certain enough to
 * stop a release. {@link #NEVER} still reports everything; it only stops the process status from
 * reflecting it.
 */
enum FailureThreshold {
    /** Fail when any finding is an error. */
    ERROR,

    /** Fail when any finding is a warning or an error. */
    WARNING,

    /** Report findings without ever failing. */
    NEVER;

    /**
     * Resolves a {@code --fail-on} value.
     *
     * @param text text supplied on the command line
     * @return the matching threshold, or empty when the text names none
     */
    static Optional<FailureThreshold> parse(String text) {
        return CanonicalName.parse(FailureThreshold.class, text);
    }

    /**
     * Decides the process status a completed run deserves.
     *
     * @param result the findings the run produced
     * @return {@link ExitCode#FINDINGS} when any finding reaches this threshold, else
     *     {@link ExitCode#CLEAN}
     */
    ExitCode verdict(VerificationResult result) {
        return result.findings().stream().anyMatch(this::reaches) ? ExitCode.FINDINGS : ExitCode.CLEAN;
    }

    private boolean reaches(Finding finding) {
        return switch (this) {
            case ERROR -> finding.severity() == Severity.ERROR;
            case WARNING -> finding.severity() != Severity.INFO;
            case NEVER -> false;
        };
    }
}
