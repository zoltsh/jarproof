package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/** Builds the diagnostic for a manifest {@code Class-Path} entry that resolves to nothing. */
final class ManifestClassPathFinding {
    private static final FindingCode CODE = FindingCode.of("JP2007");

    private ManifestClassPathFinding() {
    }

    /**
     * Reports one unresolvable manifest {@code Class-Path} entry.
     *
     * @param declaring the JAR whose manifest asks for the entry
     * @param entry the entry text exactly as the manifest wrote it
     * @param resolvedText path text the entry resolved to beside its declaring JAR
     * @return the finding
     */
    static Finding of(ClasspathEntry declaring, String entry, String resolvedText) {
        return new Finding(
                CODE,
                Severity.INFO,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(declaring.display()),
                entry,
                "manifest Class-Path entry not found",
                "This artifact's manifest asks for a companion entry that is not there. The java launcher"
                        + " skips an unresolvable Class-Path entry without saying a word, so anything the"
                        + " companion was meant to supply is simply absent at run time.",
                List.of(new Evidence("nothing readable exists at " + resolvedText)),
                List.of(new Remediation(
                        "Ship the companion artifact beside its declaring JAR, or drop the stale entry"
                                + " from the manifest.")));
    }
}
