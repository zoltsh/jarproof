package sh.zolt.jarproof.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The {@code --format} vocabulary, and the one place a run is turned into text.
 *
 * <p>Keeping the choice and the rendering together means a new format cannot be half-wired: adding
 * a constant forces a branch here, and every command gets it at once.
 */
enum ReportFormat {
    /** Compiler-style diagnostics for a person. */
    HUMAN,

    /** The versioned JSON v1 contract. */
    JSON,

    /** SARIF 2.1.0 for code-scanning tools. */
    SARIF;

    /**
     * Resolves a {@code --format} value.
     *
     * @param text text supplied on the command line
     * @return the matching format, or empty when the text names none
     */
    static Optional<ReportFormat> parse(String text) {
        return CanonicalName.parse(ReportFormat.class, text);
    }

    /**
     * Returns whether this format reports artifact paths relative to {@code --path-root}.
     *
     * <p>A person reads the paths they typed, so the human report repeats them. A machine consumer
     * reads paths that stay the same across checkouts, so both machine formats measure them from the
     * root instead (DESIGN §6).
     *
     * @return whether artifact paths are measured from the path root
     */
    boolean rootsArtifactPaths() {
        return this != HUMAN;
    }

    /**
     * Renders a completed run in this format.
     *
     * @param request the request the run answered
     * @param result the findings the run produced
     * @param sourceRoots roots that may resolve findings to source files; only SARIF reads them
     * @return the complete report, terminated by a single LF
     */
    String render(VerificationRequest request, VerificationResult result, List<Path> sourceRoots) {
        return switch (this) {
            case HUMAN -> HumanReport.render(result);
            case JSON -> JsonReport.render(request, result);
            case SARIF -> SarifReport.render(result, sourceRoots);
        };
    }
}
