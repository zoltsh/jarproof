package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Reports a sealed package that another artifact also contributes classes to.
 *
 * <p>Sealing is a promise that one archive owns a package outright, and the runtime enforces it: a
 * class from anywhere else in the same package is refused outright. Unlike a plain split package this
 * is not a style problem, it is a guaranteed failure the moment the intruding class is needed, so it
 * is an error.
 */
final class SealedPackageCheck {
    private static final FindingCode CODE = FindingCode.of("JP2008");

    private SealedPackageCheck() {
    }

    /**
     * Runs the sealed-package check.
     *
     * @param catalog the read classpath
     * @return one finding per sealing artifact whose sealed package is also claimed elsewhere
     */
    static List<Finding> run(ArtifactCatalog catalog) {
        List<Finding> findings = new ArrayList<>();
        for (IndexedArtifact artifact : catalog.artifacts()) {
            for (String sealed : artifact.sealedPackages()) {
                Set<String> contributors = catalog.artifactsByPackage().getOrDefault(sealed, Set.of());
                if (contributors.size() > 1) {
                    findings.add(finding(artifact, sealed, contributors));
                }
            }
        }
        return List.copyOf(findings);
    }

    private static Finding finding(IndexedArtifact artifact, String sealed, Set<String> contributors) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence(artifact.entry().display() + " seals this package"));
        contributors.stream()
                .filter(contributor -> !contributor.equals(artifact.entry().display()))
                .forEach(contributor -> evidence.add(new Evidence(contributor + " adds classes to it anyway")));
        return new Finding(
                CODE,
                Severity.ERROR,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact.entry().display()),
                sealed,
                "sealed package split across artifacts",
                "One archive seals this package, which is a promise that no other archive may add classes to"
                        + " it, and another archive on the effective classpath does exactly that. The runtime"
                        + " refuses to define the intruding classes.",
                evidence,
                List.of(new Remediation("Move the intruding classes into a package of their own, or stop"
                        + " sealing the package in the archive that claims it.")));
    }
}
