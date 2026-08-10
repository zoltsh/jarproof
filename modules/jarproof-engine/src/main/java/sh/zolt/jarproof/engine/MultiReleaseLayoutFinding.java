package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/** Builds the diagnostics for versioned entries the target runtime will never select. */
final class MultiReleaseLayoutFinding {
    private static final FindingCode CODE = FindingCode.of("JP3003");

    private MultiReleaseLayoutFinding() {
    }

    /**
     * Reports versioned entries in an artifact that never opted into multi-release selection.
     *
     * @param artifact artifact path text as the caller supplied it
     * @param versionsPath the version directory that will be ignored
     * @return the finding
     */
    static Finding unannounced(String artifact, String versionsPath) {
        return new Finding(
                CODE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact),
                versionsPath,
                "versioned entries without the multi-release attribute",
                "A runtime only consults versioned entries when the main manifest announces the archive as"
                        + " multi-release. This archive keeps versioned entries and makes no such"
                        + " announcement, so every runtime silently uses the base entries and the versioned"
                        + " ones are dead weight.",
                List.of(new Evidence("the main manifest announces no multi-release attribute")),
                List.of(new Remediation("Announce the archive as multi-release in its main manifest, or"
                        + " delete the versioned entries.")));
    }

    /**
     * Reports a version directory whose name no runtime can act on.
     *
     * @param artifact artifact path text as the caller supplied it
     * @param versionsPath the unusable version directory
     * @return the finding
     */
    static Finding unusableDirectory(String artifact, String versionsPath) {
        return new Finding(
                CODE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact),
                versionsPath,
                "unusable multi-release version directory",
                "Versioned entries live under a directory named for the Java release they target, and that"
                        + " name must be an integer of nine or more. This directory is named something else,"
                        + " so no runtime will ever select what it holds.",
                List.of(new Evidence("this directory name is not a Java release of nine or more")),
                List.of(new Remediation("Rename the directory to the Java release it targets, or remove it"
                        + " from the archive.")));
    }
}
