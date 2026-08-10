package sh.zolt.jarproof.cli;

import java.nio.file.Path;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The root that machine output measures artifact paths from.
 *
 * <p>The engine repeats the caller's own path text, which is what a person wants to read and what a
 * baseline needs to stay stable. A machine consumer wants something else: a path that does not
 * change when the same analysis runs from a different checkout directory, so JSON and SARIF report
 * artifacts relative to this root with forward slashes on every platform.
 *
 * <p>Only the artifact path of a finding is rewritten. Class entry names are already
 * archive-internal, subjects are bytecode symbols, and evidence is prose the engine composed, so
 * rewriting any of those would corrupt them. A path that cannot be expressed relative to the root at
 * all -- a different filesystem root, or text that is not a path on this platform -- is left exactly
 * as the caller wrote it, because inventing a path would be worse than repeating one.
 */
final class PathRoot {
    private static final char FILE_SYSTEM_SEPARATOR = '\\';
    private static final char ENTRY_SEPARATOR = '/';

    private final Path root;

    private PathRoot(Path root) {
        this.root = root;
    }

    /**
     * Chooses the root artifact paths are measured from.
     *
     * @param root the path named by {@code --path-root}
     * @return a root that rewrites artifact paths relative to it
     */
    static PathRoot of(Path root) {
        return new PathRoot(root);
    }

    /**
     * Rewrites every artifact path of a completed run.
     *
     * @param result the findings the run produced
     * @return the same findings, in the same order, measured from this root
     */
    VerificationResult rewrite(VerificationResult result) {
        List<Finding> measured = result.findings().stream().map(this::rewrite).toList();
        return new VerificationResult(measured);
    }

    private Finding rewrite(Finding finding) {
        ArtifactLocation location = finding.artifact();
        return new Finding(
                finding.code(),
                finding.severity(),
                finding.predictedError(),
                new ArtifactLocation(relative(location.artifact()), location.classEntry()),
                finding.subject(),
                finding.summary(),
                finding.explanation(),
                finding.evidence(),
                finding.remediation());
    }

    private String relative(String artifact) {
        try {
            Path measured = root.toAbsolutePath().normalize()
                    .relativize(Path.of(artifact).toAbsolutePath().normalize());
            String text = measured.toString().replace(FILE_SYSTEM_SEPARATOR, ENTRY_SEPARATOR);
            return text.isEmpty() ? artifact : text;
        } catch (IllegalArgumentException unrelated) {
            return artifact;
        }
    }
}
