package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/** Builds the diagnostic for an entry that claims to be a class file and cannot be read as one. */
final class CorruptClassFinding {
    private static final FindingCode CODE = FindingCode.of("JP3004");

    private CorruptClassFinding() {
    }

    /**
     * Reports one unreadable class entry.
     *
     * @param artifact the artifact holding the entry
     * @param entryName the entry name inside that artifact
     * @return the finding
     */
    static Finding of(ClasspathEntry artifact, String entryName) {
        return new Finding(
                CODE,
                Severity.ERROR,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(artifact.display(), entryName),
                entryName,
                "class entry cannot be read",
                "Nothing inside this entry can be verified, because no parser accepts its bytes. An entry"
                        + " that is truncated or scrambled reaches the target runtime in the same state, and"
                        + " the runtime rejects it too.",
                List.of(new Evidence("the bytes at this entry are not a well-formed class file")),
                List.of(new Remediation("Rebuild or replace the artifact so every class entry is complete.")));
    }
}
