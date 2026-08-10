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
 * Builds the diagnostic about a provider class a {@code provides} clause cannot honour.
 *
 * <p>This is the one error in the JP5xxx range, and the reason is that it is the one fault the flat
 * classpath does not excuse. Everything else a descriptor gets wrong is read only when the artifact is
 * deployed as a module; a provider that is absent, is not a subtype of its service, or cannot be
 * constructed is refused by the same {@code ServiceLoader} that refuses a broken
 * {@code META-INF/services} registration, with the same {@code ServiceConfigurationError} thrown from
 * the iterator rather than the entry being skipped — which costs the service every provider behind it.
 *
 * <p>Every fault one provider carries is reported in one finding, so the class is named once however
 * many criteria it fails. The criteria themselves arrive as text from the checks that measured them.
 */
final class ModuleProviderFinding {
    /** The fault of a provider the descriptor's own artifact does not declare. */
    static final String OUTSIDE_MODULE = "is not declared by the artifact whose descriptor registers it";

    /** The fault of a provider that does not carry the service type. */
    static final String WRONG_TYPE =
            "reaches the service type through neither its superclass chain nor its interfaces";

    private static final FindingCode UNUSABLE_PROVIDER = FindingCode.of("JP5003");
    private static final String REGISTERED_BY = "the provides clause of module ";
    private static final String FOR_SERVICE = " registers it for the service ";
    private static final String PROVIDER_CLASS = "the provider class ";

    private ModuleProviderFinding() {
    }

    /**
     * Reports a provider the module system cannot use.
     *
     * @param claim the artifact carrying the descriptor
     * @param descriptor the descriptor registering the provider
     * @param service internal name of the service it is registered under
     * @param provider internal name of the provider class
     * @param faults the criteria it fails, in reading order
     * @return the finding
     */
    static Finding of(
            ModuleClaim claim,
            ModuleDescriptor descriptor,
            String service,
            String provider,
            List<String> faults) {
        return new Finding(
                UNUSABLE_PROVIDER,
                Severity.ERROR,
                PredictedError.SERVICE_CONFIGURATION_ERROR,
                ArtifactLocation.ofClassEntry(claim.display(), descriptor.entryName()),
                provider,
                "module service provider unusable",
                "A provides clause is a module's own service registration, and it is honoured by the same"
                        + " ServiceLoader that reads configuration files on a classpath. The module system requires"
                        + " a provider to be declared by the module that registers it, to carry the service type,"
                        + " and to offer either a public no-argument constructor or a public static provider"
                        + " method returning the service. This one does not, so asking for "
                        + service + " raises a ServiceConfigurationError from the iterator and the service is left"
                        + " with no providers at all.",
                criteria(descriptor, service, faults),
                List.of(new Remediation("Correct the provides clause, or make the provider class one the module"
                        + " system can use: declared in this module, carrying the service type, and constructible"
                        + " through a public no-argument constructor or a public static provider method.")));
    }

    private static List<Evidence> criteria(ModuleDescriptor descriptor, String service, List<String> faults) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence(REGISTERED_BY + descriptor.moduleName() + FOR_SERVICE + service));
        faults.forEach(fault -> evidence.add(new Evidence(PROVIDER_CLASS + fault)));
        return List.copyOf(evidence);
    }
}
