package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Builds the diagnostics about a name in a module descriptor that resolves to nothing: a required
 * module, a consumed service type, or the module name a manifest reserves.
 *
 * <p>All three are warnings predicting no runtime failure, because the classpath this run models never
 * reads any of it. They are reported because the artifact asked to be read on a module path, and there
 * the same three names are load-bearing.
 */
final class ModuleFinding {
    private static final FindingCode UNRESOLVED_REQUIREMENT = FindingCode.of("JP5001");
    private static final FindingCode UNRESOLVED_SERVICE = FindingCode.of("JP5004");
    private static final FindingCode ILLEGAL_NAME = FindingCode.of("JP5006");
    private static final String DECLARED_BY = "the descriptor of module ";

    private ModuleFinding() {
    }

    /**
     * Reports a mandatory requirement nothing supplies.
     *
     * @param claim the artifact carrying the descriptor
     * @param descriptor the descriptor declaring the requirement
     * @param required the required module name
     * @return the finding
     */
    static Finding unresolvedRequirement(ModuleClaim claim, ModuleDescriptor descriptor, String required) {
        return new Finding(
                UNRESOLVED_REQUIREMENT,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(claim.display(), descriptor.entryName()),
                required,
                "required module resolves nowhere",
                "This artifact carries a module descriptor, so it offers itself for module-path deployment, and"
                        + " the descriptor requires a module that nothing here supplies: no supplied artifact"
                        + " declares it, no manifest reserves the name, and the target platform does not own it."
                        + " On the flat classpath this run models the descriptor is inert and nothing fails; on a"
                        + " module path resolution ends before any of this code runs.",
                List.of(
                        new Evidence(DECLARED_BY + descriptor.moduleName() + " requires that module"),
                        new Evidence("no supplied artifact declares or reserves a module of that name"),
                        new Evidence("the target platform owns no module of that name")),
                List.of(new Remediation("Supply the artifact that declares the required module, or make the"
                        + " requirement compile-only with requires static when the runtime genuinely does not"
                        + " need it.")));
    }

    /**
     * Reports a consumed service type nothing declares.
     *
     * @param claim the artifact carrying the descriptor
     * @param descriptor the descriptor declaring the clause
     * @param service internal name of the consumed service type
     * @return the finding
     */
    static Finding unresolvedService(ModuleClaim claim, ModuleDescriptor descriptor, String service) {
        return new Finding(
                UNRESOLVED_SERVICE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(claim.display(), descriptor.entryName()),
                service,
                "consumed service type resolves nowhere",
                "The descriptor declares that this module consumes a service whose type is not here at all. The"
                        + " clause itself costs nothing at run time, but the code behind it has to name the same"
                        + " type, so either the artifact that carries the service type is missing from this run"
                        + " or the clause outlived the dependency it was written for.",
                List.of(
                        new Evidence(DECLARED_BY + descriptor.moduleName() + " consumes that service"),
                        new Evidence("neither the target platform nor any supplied artifact declares that type")),
                List.of(new Remediation("Supply the artifact that declares the service type, or drop the uses"
                        + " clause along with the code that consumed it.")));
    }

    /**
     * Reports a reserved module name that is not a module name.
     *
     * @param artifact artifact path text as the caller supplied it
     * @param declared the manifest value exactly as written
     * @return the finding
     */
    static Finding illegalAutomaticName(String artifact, String declared) {
        return new Finding(
                ILLEGAL_NAME,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact),
                declared,
                "reserved automatic module name is not a legal module name",
                "This artifact has no module descriptor and reserves a module name in its manifest instead, but"
                        + " the value is not a module name: a module name is one or more Java identifiers joined"
                        + " by single dots, with no empty segment. A classpath run never reads the attribute, so"
                        + " nothing fails here; on a module path no module can be derived from this artifact at"
                        + " all, and every descriptor that requires it fails to resolve.",
                List.of(
                        new Evidence("the manifest reserves that value for the module path"),
                        new Evidence("at least one segment of it is not a Java identifier")),
                List.of(new Remediation("Correct the manifest attribute to a dotted sequence of Java"
                        + " identifiers, such as the reverse-domain name the artifact publishes under.")));
    }
}
