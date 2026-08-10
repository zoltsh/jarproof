package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Builds the diagnostics about a provider class a service configuration file names: it is absent, it
 * does not carry the service type, or it cannot be constructed.
 *
 * <p>All three are errors predicting one {@code ServiceConfigurationError}, and all three are
 * reported against the provider's internal name at the file that registers it, so a reader is told
 * which line of which resource to edit. The runtime raises that error from the iterator rather than
 * skipping the entry, which is why a single bad registration costs the service every provider behind
 * it — every explanation says so, because that consequence is the reason these are not warnings.
 */
final class ServiceProviderFinding {
    private static final FindingCode MISSING = FindingCode.of("JP4001");
    private static final FindingCode WRONG_TYPE = FindingCode.of("JP4002");
    private static final FindingCode NOT_INSTANTIABLE = FindingCode.of("JP4005");
    private static final String NAMED_ON_LINE = "the provider is named on line ";
    private static final String PROVIDER = "the provider ";
    private static final String CRITERION_SEPARATOR = ", ";

    private ServiceProviderFinding() {
    }

    /**
     * Reports a provider nothing declares.
     *
     * @param declaration the file registering the provider
     * @param entry the line naming it
     * @return the finding
     */
    static Finding missingProvider(ServiceDeclaration declaration, ServiceProviderEntry entry) {
        return new Finding(
                MISSING,
                Severity.ERROR,
                PredictedError.SERVICE_CONFIGURATION_ERROR,
                declaration.location(),
                entry.internalName(),
                "service provider class missing",
                "This file registers a provider class that nothing supplies: no artifact on the effective"
                        + " classpath declares it and the target platform does not either. Failing to reach a"
                        + " registered provider is never skipped quietly, so it surfaces as a"
                        + " ServiceConfigurationError from the iterator, which ends the iteration and takes"
                        + " every provider behind it down as well.",
                List.of(
                        new Evidence(NAMED_ON_LINE + entry.lineNumber()),
                        new Evidence("no artifact on the effective classpath and no platform module declares"
                                + " that class")),
                List.of(new Remediation("Add the artifact that declares the provider, or remove the stale"
                        + " provider entry.")));
    }

    /**
     * Reports a provider that is not a subtype of the service it is registered under.
     *
     * @param declaration the file registering the provider
     * @param entry the line naming it
     * @return the finding
     */
    static Finding wrongServiceType(ServiceDeclaration declaration, ServiceProviderEntry entry) {
        return new Finding(
                WRONG_TYPE,
                Severity.ERROR,
                PredictedError.SERVICE_CONFIGURATION_ERROR,
                declaration.location(),
                entry.internalName(),
                "service provider does not implement the service type",
                "The provider class exists, but nothing in its superclass chain or its interfaces is the"
                        + " service type it is registered under. Every provider is measured against the"
                        + " service type before anything is constructed, and one that is not a subtype"
                        + " becomes a ServiceConfigurationError from the iterator, so this single"
                        + " registration disables every provider for the service.",
                List.of(
                        new Evidence(NAMED_ON_LINE + entry.lineNumber()),
                        new Evidence("its superclass chain and interfaces never reach "
                                + declaration.serviceName())),
                List.of(new Remediation("Implement the service type on the provider, or register the provider"
                        + " under the service type it actually implements.")));
    }

    /**
     * Reports a provider a classpath {@code ServiceLoader} cannot construct.
     *
     * @param declaration the file registering the provider
     * @param entry the line naming it
     * @param failures the criteria the provider fails, in reading order
     * @return the finding
     */
    static Finding notInstantiable(
            ServiceDeclaration declaration, ServiceProviderEntry entry, List<String> failures) {
        return new Finding(
                NOT_INSTANTIABLE,
                Severity.ERROR,
                PredictedError.SERVICE_CONFIGURATION_ERROR,
                declaration.location(),
                entry.internalName(),
                "service provider not instantiable",
                "A provider found on a classpath is constructed through its public no-argument constructor and"
                        + " there is no other way in, so one that cannot be constructed leaves the service with"
                        + " no providers at all and a ServiceConfigurationError in its place. This provider "
                        + String.join(CRITERION_SEPARATOR, failures) + ".",
                criteria(entry, failures),
                List.of(new Remediation("Give the provider a public no-argument constructor and make its class"
                        + " public and concrete, or register a small public adapter that constructs it.")));
    }

    private static List<Evidence> criteria(ServiceProviderEntry entry, List<String> failures) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence(NAMED_ON_LINE + entry.lineNumber()));
        failures.forEach(failure -> evidence.add(new Evidence(PROVIDER + failure)));
        return List.copyOf(evidence);
    }
}
