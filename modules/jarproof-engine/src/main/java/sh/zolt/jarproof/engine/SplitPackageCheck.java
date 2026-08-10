package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Reports packages that more than one artifact contributes classes to.
 *
 * <p>A flat classpath merges the contributions and nothing breaks, which is why this stays
 * informational. It still matters: the package has no single owner, so package-private access
 * crosses an artifact boundary nobody declared, and the next version of either side can shadow the
 * other. The unnamed package is skipped because there is no prefix to name.
 */
final class SplitPackageCheck {
    private static final FindingCode CODE = FindingCode.of("JP2003");

    private SplitPackageCheck() {
    }

    /**
     * Runs the split-package check.
     *
     * @param catalog the read classpath
     * @return one finding per package that more than one artifact contributes to
     */
    static List<Finding> run(ArtifactCatalog catalog) {
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, Set<String>> split : catalog.artifactsByPackage().entrySet()) {
            if (split.getValue().size() > 1) {
                findings.add(finding(split.getKey(), split.getValue()));
            }
        }
        return List.copyOf(findings);
    }

    private static Finding finding(String packageName, Set<String> contributors) {
        return new Finding(
                CODE,
                Severity.INFO,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(contributors.iterator().next()),
                packageName,
                "package split across artifacts",
                "Classes in this package come from more than one artifact. A flat classpath merges them, so"
                        + " nothing fails today, but the package has no single owner and either side can start"
                        + " shadowing the other without warning.",
                contributors.stream()
                        .map(contributor -> new Evidence(contributor + " contributes classes to this package"))
                        .toList(),
                List.of(new Remediation("Give the package one owning artifact, or rename one side so the two"
                        + " no longer overlap.")));
    }
}
