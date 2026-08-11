package sh.zolt.jarproof.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.ArtifactSummary;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The root that machine output measures artifact paths from.
 *
 * <p>The engine repeats the caller's own path text, which is what a person wants to read. A machine
 * consumer wants something else: a path that does not change when the same analysis runs from a
 * different checkout directory, so JSON and SARIF report artifacts relative to this root with forward
 * slashes on every platform. The human report keeps the caller's spelling on purpose -- only the
 * machine formats promise byte stability, and only they pay for it by measuring paths (see
 * {@link ReportFormat#rootsArtifactPaths()}).
 *
 * <p>Two kinds of text are measured. The artifact path of a finding is one; the path spellings inside
 * the prose the engine composed are the other, rewritten through {@link EvidenceText} so a sentence
 * beside a measured artifact cannot still name a directory that exists on one machine. Class entry
 * names are already archive-internal, a source file name is the bare name a compiler recorded, and
 * subjects are bytecode symbols, so none of those is touched.
 *
 * <p>A position inside an application archive is displayed {@code outer!/inner}: the outer archive is
 * the caller's text and the inner part is an entry name inside it, so only the outer segment is
 * measured and the entry travels unchanged.
 *
 * <p>Two cases are answered with the caller's own normalised text instead of a measured one. A path
 * that would have to climb out of the root produces a {@code ../..} chain whose length says how deep
 * the checkout directory sits, which is machine-specific in exactly the way this root exists to
 * avoid; and a root with no name elements at all -- the file system root -- would strip the leading
 * separator off every absolute path, turning it into text that reads as relative and resolves only
 * from {@code /}. Text that is not a path on this platform is passed through untouched, because
 * inventing a path would be worse than repeating one.
 */
final class PathRoot {
    private static final char FILE_SYSTEM_SEPARATOR = '\\';
    private static final char ENTRY_SEPARATOR = '/';
    private static final String NESTED_SEPARATOR = "!/";
    private static final String CLIMB = "..";
    private static final String NOT_A_DIRECTORY = "This path root is not a directory that exists: ";

    private final Path root;

    private PathRoot(Path root) {
        this.root = root;
    }

    /**
     * Chooses the root artifact paths are measured from.
     *
     * @param root the path named by {@code --path-root}
     * @return a root that measures artifact paths against it
     * @throws IllegalArgumentException when the path is not a directory that exists, which would
     *     otherwise be reported as every artifact sitting some number of levels below it
     */
    static PathRoot of(Path root) {
        Path measured = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(measured)) {
            throw new IllegalArgumentException(NOT_A_DIRECTORY + root);
        }
        return new PathRoot(measured);
    }

    /**
     * Rewrites every path a completed run reports.
     *
     * <p>What the run examined travels with it unchanged. Measuring a path from a root says nothing
     * about how many classes were read, so a rewritten result that dropped its tallies would report
     * less about the same run than the one it was made from.
     *
     * @param request the request the run answered, which supplies the spellings to rewrite
     * @param result the findings the run produced
     * @return the same findings and the same tallies, in the same order, measured from this root
     */
    VerificationResult rewrite(VerificationRequest request, VerificationResult result) {
        EvidenceText spellings = EvidenceText.of(this, request);
        List<Finding> measured =
                result.findings().stream().map(finding -> rewritten(finding, spellings)).toList();
        return new VerificationResult(
                measured, result.analyzedClassCount(), result.analyzedArtifactCount());
    }

    /**
     * Rewrites the artifact path of one inspection.
     *
     * @param facts the facts the inspection read
     * @return the same facts, naming the artifact from this root
     */
    ArtifactSummary rewrite(ArtifactSummary facts) {
        return new ArtifactSummary(
                artifact(facts.artifact()),
                facts.entryCount(),
                facts.classCount(),
                facts.nestedArchiveCount(),
                facts.bytecodeLevels(),
                facts.declaredServices(),
                facts.multiReleaseVersions());
    }

    /**
     * Measures one artifact display text from this root.
     *
     * @param display the path text the caller supplied, possibly naming a position inside an archive
     * @return the spelling machine output reports it under
     */
    String artifact(String display) {
        int nested = display.indexOf(NESTED_SEPARATOR);
        return nested < 0
                ? measured(display)
                : measured(display.substring(0, nested)) + display.substring(nested);
    }

    private Finding rewritten(Finding finding, EvidenceText spellings) {
        ArtifactLocation location = finding.artifact();
        return new Finding(
                finding.code(),
                finding.severity(),
                finding.predictedError(),
                new ArtifactLocation(
                        artifact(location.artifact()),
                        location.classEntry(),
                        location.sourceFile(),
                        location.line()),
                finding.subject(),
                finding.summary(),
                finding.explanation(),
                spellings.measured(finding.evidence()),
                finding.remediation());
    }

    private String measured(String display) {
        try {
            Path supplied = Path.of(display);
            String normalised = slashed(supplied.normalize().toString());
            if (normalised.isEmpty()) {
                return display;
            }
            if (root.getNameCount() == 0) {
                return normalised;
            }
            Path relative = root.relativize(supplied.toAbsolutePath().normalize());
            String spelling = slashed(relative.toString());
            return spelling.isEmpty() || climbsOut(relative) ? normalised : spelling;
        } catch (IllegalArgumentException unrelated) {
            return display;
        }
    }

    private static boolean climbsOut(Path relative) {
        for (Path element : relative) {
            if (element.toString().equals(CLIMB)) {
                return true;
            }
        }
        return false;
    }

    private static String slashed(String text) {
        return text.replace(FILE_SYSTEM_SEPARATOR, ENTRY_SEPARATOR);
    }
}
