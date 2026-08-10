package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.TargetRuntime;

/**
 * Reports class files the target runtime would refuse before running anything.
 *
 * <p>Two independent rules. A class file compiled for a newer release than the target is refused
 * outright. A preview class file is refused unless the target is exactly the release that produced it
 * and preview features are enabled, because the preview marker deliberately binds a class file to one
 * release. A preview class file from a newer release breaks both rules and is reported twice, since
 * fixing either one alone still leaves the classpath broken.
 */
final class ClassFileVersionCheck {
    private static final FindingCode NEWER = FindingCode.of("JP3001");
    private static final FindingCode PREVIEW = FindingCode.of("JP3002");
    private static final int MAJOR_VERSION_BASE = 44;
    private static final int PREVIEW_MINOR_VERSION = 0xFFFF;

    private final int targetRelease;
    private final PreviewMode previewMode;

    private ClassFileVersionCheck(TargetRuntime runtime) {
        this.targetRelease = runtime.javaRelease();
        this.previewMode = runtime.previewMode();
    }

    /**
     * Runs the class file version checks.
     *
     * @param catalog the read classpath
     * @param runtime the target runtime
     * @return one finding per rule each offending class file breaks
     */
    static List<Finding> run(ArtifactCatalog catalog, TargetRuntime runtime) {
        ClassFileVersionCheck check = new ClassFileVersionCheck(runtime);
        List<Finding> findings = new ArrayList<>();
        for (IndexedArtifact artifact : catalog.artifacts()) {
            for (IndexedClass declared : artifact.classes()) {
                check.inspect(findings, artifact, declared);
            }
        }
        return List.copyOf(findings);
    }

    private void inspect(List<Finding> findings, IndexedArtifact artifact, IndexedClass declared) {
        if (declared.classFileMajor() > highestAcceptedMajor()) {
            findings.add(newerThanTarget(artifact, declared));
        }
        boolean acceptedPreview =
                previewMode == PreviewMode.ENABLED && declared.classFileMajor() == highestAcceptedMajor();
        if (declared.classFileMinor() == PREVIEW_MINOR_VERSION && !acceptedPreview) {
            findings.add(invalidPreview(artifact, declared));
        }
    }

    private Finding newerThanTarget(IndexedArtifact artifact, IndexedClass declared) {
        return new Finding(
                NEWER,
                Severity.ERROR,
                PredictedError.UNSUPPORTED_CLASS_VERSION_ERROR,
                ArtifactLocation.ofClassEntry(artifact.entry().display(), declared.entryName()),
                declared.internalName(),
                "class file newer than the target Java release",
                "This class file was compiled for a newer Java release than the classpath will run on. The"
                        + " target runtime refuses it before any of its code runs, so nothing inside it can be"
                        + " reached at all.",
                List.of(
                        new Evidence("the class file targets Java " + releaseOf(declared.classFileMajor())),
                        new Evidence("the target runtime accepts Java " + targetRelease + " at most")),
                List.of(new Remediation(
                        "Recompile the artifact for the target release, or raise the target to match it.")));
    }

    private Finding invalidPreview(IndexedArtifact artifact, IndexedClass declared) {
        return new Finding(
                PREVIEW,
                Severity.ERROR,
                PredictedError.UNSUPPORTED_CLASS_VERSION_ERROR,
                ArtifactLocation.ofClassEntry(artifact.entry().display(), declared.entryName()),
                declared.internalName(),
                "preview class file invalid for the target runtime",
                "A preview marker binds a class file to the exact Java release that produced it, and that"
                        + " release also has to be started with preview features enabled. One of those does not"
                        + " hold here, so the runtime rejects the class file.",
                List.of(
                        new Evidence("the class file is marked as depending on preview features"),
                        new Evidence("it is usable only on Java " + releaseOf(declared.classFileMajor())
                                + " with preview features enabled")),
                List.of(new Remediation("Rebuild the artifact without preview features, or target exactly the"
                        + " release it was compiled with and enable preview.")));
    }

    private int highestAcceptedMajor() {
        return MAJOR_VERSION_BASE + targetRelease;
    }

    private static int releaseOf(int classFileMajor) {
        return classFileMajor - MAJOR_VERSION_BASE;
    }
}
