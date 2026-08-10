package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Reports one published artifact appearing on the classpath at more than one version.
 *
 * <p>The identity comes from what each archive claims about itself, so this is a heuristic and says
 * so: a repackaged archive claims nothing and is skipped rather than guessed at. When two archives do
 * claim the same identity at different versions, the classpath is silently running a mixture of both,
 * which is exactly the drift that produces linkage failures nobody can reproduce.
 */
final class ArtifactVersionCheck {
    private static final FindingCode CODE = FindingCode.of("JP2004");

    private ArtifactVersionCheck() {
    }

    /**
     * Runs the duplicate-version check.
     *
     * @param catalog the read classpath
     * @return one finding per identity that appears at more than one version
     */
    static List<Finding> run(ArtifactCatalog catalog) {
        Map<String, Map<String, String>> claimed = new TreeMap<>();
        for (IndexedArtifact artifact : catalog.artifacts()) {
            artifact.coordinate().ifPresent(coordinate -> claimed
                    .computeIfAbsent(coordinate.name(), absent -> new LinkedHashMap<>())
                    .putIfAbsent(coordinate.version(), artifact.entry().display()));
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> identity : claimed.entrySet()) {
            if (identity.getValue().size() > 1) {
                findings.add(finding(identity.getKey(), identity.getValue()));
            }
        }
        return List.copyOf(findings);
    }

    private static Finding finding(String identity, Map<String, String> versions) {
        return new Finding(
                CODE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(versions.values().iterator().next()),
                identity,
                "one artifact present at several versions",
                "Two or more archives on the effective classpath claim to be the same published artifact at"
                        + " different versions. Only the classes reached first are used, so the classpath ends"
                        + " up running a mixture of the versions rather than any one of them.",
                versions.entrySet().stream()
                        .map(version -> new Evidence(version.getKey() + " is claimed by " + version.getValue()))
                        .toList(),
                List.of(new Remediation(
                        "Settle the classpath on one version of this artifact and remove the others.")));
    }
}
