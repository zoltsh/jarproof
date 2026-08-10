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
 * <p>Everything in it is text the caller supplied or the engine derived from bytecode -- never an
 * absolute path, a line number, or a message -- so rebuilding the same inputs reproduces the same
 * fingerprints, and rewording a summary does not silently invalidate an accepted baseline.
 */
final class BaselineFingerprint {
    private static final char PART_SEPARATOR = '|';

    private BaselineFingerprint() {
    }

    /**
     * Computes the fingerprint of one finding.
     *
     * @param finding the finding to identify
     * @return the four-part fingerprint
     */
    static String of(Finding finding) {
        ArtifactLocation location = finding.artifact();
        return finding.code().value() + PART_SEPARATOR
                + location.artifact() + PART_SEPARATOR
                + location.classEntry().orElse("") + PART_SEPARATOR
                + finding.subject();
    }
}
