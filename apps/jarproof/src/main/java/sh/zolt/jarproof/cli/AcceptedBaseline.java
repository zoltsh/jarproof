package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * @return the findings that are new, in the order the engine produced them
     */
    VerificationResult applyTo(
            VerificationRequest request, String profile, VerificationResult result, PrintWriter err) {
        warnAboutDrift(request, profile, err);
        BaselineComparison comparison = BaselineComparison.against(result, document);
        err.println(SUPPRESSED + comparison.suppressed() + ACCEPTED + comparison.stale().size() + STALE);
        err.flush();
        return new VerificationResult(comparison.newFindings());
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
