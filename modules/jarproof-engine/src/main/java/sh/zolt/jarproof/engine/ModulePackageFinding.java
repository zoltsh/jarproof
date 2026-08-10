package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Builds the diagnostics about a package the module system would refuse: one a descriptor exposes but
 * does not contain, and one that two module-capable artifacts both contain.
 *
 * <p>Both are warnings predicting no runtime failure. A flat classpath has no opinion about either —
 * it never reads an exports clause, and it merges two contributions to one package without complaint,
 * which is all JP2003 reports and why that finding is informational. The module system has an opinion
 * about both, and these findings exist so an artifact that asked to be read there is measured by its
 * rules.
 */
final class ModulePackageFinding {
    private static final FindingCode EMPTY_EXPOSURE = FindingCode.of("JP5002");
    private static final FindingCode SPLIT_PACKAGE = FindingCode.of("JP5005");
    private static final String CONTRIBUTES_AS = " contributes classes to it as module ";

    private ModulePackageFinding() {
    }

    /**
     * Reports a package a descriptor exposes and the artifact does not hold.
     *
     * @param claim the artifact carrying the descriptor
     * @param descriptor the descriptor exposing the package
     * @param packagePrefix the exposed package internal prefix
     * @return the finding
     */
    static Finding emptyExposure(ModuleClaim claim, ModuleDescriptor descriptor, String packagePrefix) {
        return new Finding(
                EMPTY_EXPOSURE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(claim.display(), descriptor.entryName()),
                packagePrefix,
                "exposed package holds no classes here",
                "The descriptor exports or opens a package that this artifact declares no class in. A classpath"
                        + " never reads the clause, so nothing fails in the deployment this run models. A module"
                        + " path does read it, and a module that exposes a package it does not contain is rejected"
                        + " when the module is defined, before any of its code runs — which usually means the"
                        + " package was renamed, moved to another artifact, or never shipped at all.",
                List.of(
                        new Evidence("module " + descriptor.moduleName() + " exports or opens that package"),
                        new Evidence("this artifact declares no class in it")),
                List.of(new Remediation("Remove the clause, correct the package name, or ship the classes the"
                        + " package is meant to hold.")));
    }

    /**
     * Reports one package that more than one module-capable artifact contains.
     *
     * @param packagePrefix the package internal prefix
     * @param contributors the module-capable artifacts contributing to it, in classpath order
     * @return the finding
     */
    static Finding splitPackage(String packagePrefix, List<ModuleClaim> contributors) {
        return new Finding(
                SPLIT_PACKAGE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(contributors.get(0).display()),
                packagePrefix,
                "package split across module-capable artifacts",
                "More than one artifact that advertises module-path deployability declares classes in this"
                        + " package. On the flat classpath this run models the contributions merge and nothing"
                        + " fails, which is exactly what JP2003 reports as information. The module system refuses"
                        + " the layout outright: a package belongs to one module and no more, so these artifacts"
                        + " cannot be read together as modules however they are ordered.",
                contributions(contributors),
                List.of(new Remediation("Give the package a single owning module, or keep the artifacts that"
                        + " overlap off the module path and read them as plain classpath libraries.")));
    }

    private static List<Evidence> contributions(List<ModuleClaim> contributors) {
        List<Evidence> evidence = new ArrayList<>();
        for (ModuleClaim contributor : contributors) {
            contributor.claimedModuleName()
                    .ifPresent(name -> evidence.add(new Evidence(contributor.display() + CONTRIBUTES_AS + name)));
        }
        return List.copyOf(evidence);
    }
}
