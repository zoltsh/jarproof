package sh.zolt.jarproof.engine;

import java.nio.file.Path;
import java.util.Objects;
import sh.zolt.jarproof.api.ArtifactSummary;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * Verifies that a set of Java artifacts will work together on a given runtime, and reports the
 * layout facts of one artifact on its own.
 *
 * <p>This is the engine's whole surface. A run reads bytes and metadata only: analysed classes are
 * never defined or executed, no archive is ever extracted, and nothing is fetched, so the same inputs
 * can be verified anywhere, including inside a build that must not reach the network.
 *
 * <p>A run is deterministic. Findings are returned in one canonical order derived from the inputs, and
 * every artifact path in them is the text the caller supplied rather than a path resolved on this
 * machine, so two runs over the same inputs produce identical reports.
 *
 * <p>Invalid input fails fast rather than producing a partial report: a missing or unreadable artifact
 * raises {@link IllegalArgumentException}, and input that exceeds one of the engine's documented
 * resource ceilings raises {@link IllegalStateException} naming the ceiling. A finding, by contrast, is
 * a fact about otherwise readable input, and analysis always continues past one.
 *
 * <p>What a run checks grows with each release. Callers should treat the result as a set of coded
 * findings to render or gate on, never as a fixed list of questions asked.
 */
public final class Jarproof {
    private Jarproof() {
    }

    /**
     * Verifies one request.
     *
     * @param request the artifacts, classpath, target runtime, and scope to verify
     * @return every finding the run produced, in canonical order
     * @throws NullPointerException when the request is missing
     * @throws IllegalArgumentException when an artifact or classpath entry cannot be read, or when
     *     the target release has no bundled platform symbols
     * @throws IllegalStateException when the input exceeds one of the engine's resource ceilings
     */
    public static VerificationResult verify(VerificationRequest request) {
        Objects.requireNonNull(request, "A verification needs a request");
        return VerificationRun.execute(request);
    }

    /**
     * Reports the facts one artifact presents on its own, with nothing to compare it against.
     *
     * <p>These are raw layout facts rather than diagnostics: entry and class counts, the class file
     * versions the classes declare, the services the artifact registers providers for, and the
     * multi-release directories it carries. Nothing is selected for a target release, so a
     * multi-release archive reports every version directory it holds instead of the one a runtime
     * would choose.
     *
     * @param artifact path to a JAR or a class directory, kept exactly as the caller supplied it
     * @return the artifact's layout facts, with every list in canonical order
     * @throws NullPointerException when the artifact is missing
     * @throws IllegalArgumentException when the path is neither a readable archive nor a readable
     *     class directory
     * @throws IllegalStateException when the artifact exceeds one of the engine's resource ceilings
     */
    public static ArtifactSummary inspect(Path artifact) {
        Objects.requireNonNull(artifact, "An inspection needs an artifact");
        return Inspection.of(artifact);
    }
}
