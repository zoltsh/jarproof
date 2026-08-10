package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * A baseline file on disk, and what applying it to a fresh run means.
 *
 * <p>Applying one is not silent. The suppressed count says how much of the report was accepted
 * earlier, and the stale count says how many accepted entries no longer occur -- the number that
 * lets a team ratchet the file down instead of letting it become permanent permission. Both go to
 * the diagnostic stream, because the findings stream carries findings and nothing else.
 *
 * <p>Counting stale entries is only half of a ratchet, so the file can also be pruned in place: the
 * accepted entries a run no longer observes are dropped, and nothing else about the file moves. That
 * is a side effect on the file and never on the verdict -- a run reports the same findings with the
 * same status whether or not it rewrote the baseline it read.
 *
 * <p>A baseline recorded against a different target release, preview policy, scope, or runtime
 * symbol profile is not the same judgement as the run reading it. Alpha.1 warns about each such
 * disagreement and applies the file anyway: refusing it would strand a project mid-migration, while
 * applying it silently would hide the fact that the acceptance no longer means what it says.
 */
final class AcceptedBaseline {
    private static final String RECORDS = " records ";
    private static final String MEASURED = ", and this run measured ";
    private static final String APPLIED_ANYWAY = "; applying it anyway";
    private static final String SUPPRESSED = "suppressed ";
    private static final String ACCEPTED = " accepted findings; ";
    private static final String STALE = " baseline entries are stale";
    private static final String PRUNED = "pruned ";
    private static final String PRUNED_ENTRIES = " stale baseline entries";
    private static final String UNPRUNABLE = "This baseline cannot be rewritten: ";

    private final Path file;
    private final BaselineDocument document;

    private AcceptedBaseline(Path file, BaselineDocument document) {
        this.file = file;
        this.document = document;
    }

    /**
     * Reads a baseline file.
     *
     * @param file the path named by {@code --baseline}
     * @return the accepted findings it records
     * @throws IllegalArgumentException when the file does not exist or is not a baseline this build
     *     understands
     * @throws IOException when reading the file fails after it was found
     */
    static AcceptedBaseline read(Path file) throws IOException {
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException("This baseline does not exist or cannot be read: " + file);
        }
        return new AcceptedBaseline(file, BaselineJson.read(Files.readString(file, StandardCharsets.UTF_8)));
    }

    /**
     * Applies the baseline to a fresh run, reporting what it suppressed and what has gone stale.
     *
     * @param request the request the fresh run answered
     * @param profile identity of the runtime symbol profile the fresh run used
     * @param result the findings the fresh run produced
     * @param err the stream every diagnostic note goes to
     * @return what the fresh run looks like once this baseline is applied
     */
    BaselineComparison applyTo(
            VerificationRequest request, String profile, VerificationResult result, PrintWriter err) {
        warnAboutDrift(request, profile, err);
        BaselineComparison comparison = BaselineComparison.against(result, document);
        err.println(SUPPRESSED + comparison.suppressed() + ACCEPTED + comparison.stale().size() + STALE);
        err.flush();
        return comparison;
    }

    /**
     * Rewrites the file to hold exactly the accepted fingerprints this run still observed.
     *
     * <p>Only the fingerprint list moves, and it only ever shortens. Every envelope field is the one
     * already in the file, so a prune cannot rebrand a baseline as recorded against a release, scope,
     * or profile it was not, and the fingerprints stay the sorted, duplicate-free list every writer
     * produces.
     *
     * <p>A run with nothing stale writes nothing at all rather than rewriting identical bytes, so the
     * file's timestamp still means the baseline moved. Whether the file can be rewritten is settled
     * before that decision, because asking to prune a file that cannot be written should fail the
     * same way however much this particular run happened to find stale.
     *
     * @param comparison what applying this baseline produced
     * @param err the stream every diagnostic note goes to
     * @throws IllegalArgumentException when the file cannot be rewritten
     * @throws IOException when rewriting the file fails
     */
    void pruneStale(BaselineComparison comparison, PrintWriter err) throws IOException {
        if (!Files.isWritable(file)) {
            throw new IllegalArgumentException(UNPRUNABLE + file);
        }
        Set<String> stale = Set.copyOf(comparison.stale());
        err.println(PRUNED + stale.size() + PRUNED_ENTRIES);
        err.flush();
        if (stale.isEmpty()) {
            return;
        }
        List<String> observed = document.fingerprints().stream()
                .filter(fingerprint -> !stale.contains(fingerprint))
                .toList();
        OutputTarget.of(Optional.of(file), err).write(BaselineJson.write(document.withFingerprints(observed)));
    }

    private void warnAboutDrift(VerificationRequest request, String profile, PrintWriter err) {
        warnIfDifferent(
                err,
                RequestJson.TARGET_JAVA,
                String.valueOf(document.targetJava()),
                String.valueOf(request.targetRuntime().javaRelease()));
        warnIfDifferent(
                err,
                RequestJson.PREVIEW,
                CanonicalName.of(document.preview()),
                CanonicalName.of(request.targetRuntime().previewMode()));
        warnIfDifferent(err, RequestJson.SCOPE, CanonicalName.of(document.scope()), CanonicalName.of(request.scope()));
        warnIfDifferent(err, BaselineJson.PROFILE, document.profile(), profile);
    }

    private void warnIfDifferent(PrintWriter err, String field, String recorded, String current) {
        if (!recorded.equals(current)) {
            err.println(file + RECORDS + field + ' ' + recorded + MEASURED + current + APPLIED_ANYWAY);
            err.flush();
        }
    }
}
