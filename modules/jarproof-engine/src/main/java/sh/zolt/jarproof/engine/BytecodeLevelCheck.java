package sh.zolt.jarproof.engine;

import java.util.ArrayList;
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
 * Reports an artifact whose class files were compiled for several Java releases.
 *
 * <p>Perfectly legal, and normal in a repackaged archive, so this stays informational. It is worth
 * saying out loud because the artifact's real minimum runtime is set by its newest class file rather
 * than by anything it declares, and that number is usually a surprise. Levels are counted after
 * multi-release selection, so the histogram is the one the target runtime would actually see.
 */
final class BytecodeLevelCheck {
    private static final FindingCode CODE = FindingCode.of("JP3005");

    private BytecodeLevelCheck() {
    }

    /**
     * Runs the mixed-bytecode-level check.
     *
     * @param catalog the read classpath
     * @return one finding per artifact that mixes class file versions
     */
    static List<Finding> run(ArtifactCatalog catalog) {
        List<Finding> findings = new ArrayList<>();
        for (IndexedArtifact artifact : catalog.artifacts()) {
            Map<Integer, Integer> levels = levels(artifact);
            if (levels.size() > 1) {
                findings.add(finding(artifact, levels));
            }
        }
        return List.copyOf(findings);
    }

    private static Map<Integer, Integer> levels(IndexedArtifact artifact) {
        Map<Integer, Integer> counted = new TreeMap<>();
        for (IndexedClass declared : artifact.classes()) {
            counted.merge(declared.classFileMajor(), 1, Integer::sum);
        }
        return counted;
    }

    private static Finding finding(IndexedArtifact artifact, Map<Integer, Integer> levels) {
        return new Finding(
                CODE,
                Severity.INFO,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact.entry().display()),
                artifact.entry().display(),
                "mixed bytecode levels within one artifact",
                "The class files in this artifact were compiled for more than one Java release. That is legal,"
                        + " and usual in a repackaged archive, but it means the artifact's real minimum runtime"
                        + " comes from its newest class file rather than from anything it declares.",
                levels.entrySet().stream()
                        .map(level -> new Evidence(
                                "class file version " + level.getKey() + " appears " + level.getValue() + " times"))
                        .toList(),
                List.of(new Remediation("Compile the artifact against a single release so its runtime"
                        + " requirement is stated once and obvious.")));
    }
}
