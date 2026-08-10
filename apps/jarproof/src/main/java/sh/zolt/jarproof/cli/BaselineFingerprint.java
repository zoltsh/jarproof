package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Finding;

/**
 * The stable identity a baseline remembers a finding by.
 *
 * <p>A fingerprint is the diagnostic code, the artifact, the class entry, and the subject symbol,
 * joined by vertical bars: {@code JP1003|app.jar|com/acme/Order.class|com/acme/Api#read()V}. A
 * finding about a whole artifact leaves the third part empty rather than dropping it, so the four
 * parts always line up.
 *
 * <p>The artifact part is measured from {@code --path-root} exactly as machine output measures it,
 * rather than repeated as the caller spelled it. Two invocations naming the same file as
 * {@code ./lib/api.jar} and {@code lib/api.jar} describe the same finding, and a baseline that
 * called them different findings would report the accepted one as new and the recorded one as stale
 * -- the one thing a baseline exists to prevent. Measuring also keeps the recorded text portable: a
 * fingerprint carries no absolute path unless the artifact really sits outside the root.
 *
 * <p>Everything else in it is text the engine derived from bytecode -- never a line number and never
 * a message -- so rebuilding the same inputs reproduces the same fingerprints, and rewording a
 * summary does not silently invalidate an accepted baseline.
 */
final class BaselineFingerprint {
    private static final char PART_SEPARATOR = '|';

    private BaselineFingerprint() {
    }

    /**
     * Computes the fingerprint of one finding.
     *
     * @param root the root artifact paths are measured from
     * @param finding the finding to identify
     * @return the four-part fingerprint
     */
    static String of(PathRoot root, Finding finding) {
        ArtifactLocation location = finding.artifact();
        return finding.code().value() + PART_SEPARATOR
                + root.artifact(location.artifact()) + PART_SEPARATOR
                + location.classEntry().orElse("") + PART_SEPARATOR
                + finding.subject();
    }
}
