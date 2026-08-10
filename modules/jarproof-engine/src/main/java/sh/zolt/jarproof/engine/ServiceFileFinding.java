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
 * Builds the diagnostics about the text of a service configuration file rather than about the
 * classes it names: a name the format does not allow, and a provider registered twice.
 *
 * <p>Both are reported against the service the file registers providers for, because the file is
 * what has to be edited. A malformed name is an error: the format is narrow on purpose and a name
 * outside it costs the service every provider the file lists. A repeat is information: the runtime
 * ignores it, and the duplication is a symptom of something else worth knowing.
 */
final class ServiceFileFinding {
    private static final FindingCode MALFORMED = FindingCode.of("JP4003");
    private static final FindingCode DUPLICATE = FindingCode.of("JP4004");
    private static final String MALFORMED_SUMMARY = "malformed service configuration file";

    private ServiceFileFinding() {
    }

    /**
     * Reports one line the format does not allow.
     *
     * @param declaration the file holding the line
     * @param entry the offending line, kept verbatim
     * @return the finding
     */
    static Finding malformedProviderLine(ServiceDeclaration declaration, ServiceProviderEntry entry) {
        return new Finding(
                MALFORMED,
                Severity.ERROR,
                PredictedError.SERVICE_CONFIGURATION_ERROR,
                declaration.location(),
                declaration.serviceInternalName(),
                MALFORMED_SUMMARY,
                "A service configuration file carries one provider binary name per line and nothing else, so a"
                        + " line that is not a name at all stops the parse before a single provider has been"
                        + " constructed. Every provider this file registers is lost, not only the offending one.",
                List.of(new Evidence("the entry on line " + entry.lineNumber()
                        + " is not a binary class name: " + entry.declaredName())),
                List.of(new Remediation("Rewrite the offending line as one fully qualified provider class name,"
                        + " or delete it.")));
    }

    /**
     * Reports a file whose own name is not a service type name.
     *
     * @param declaration the file, named after the service it registers providers for
     * @return the finding
     */
    static Finding malformedServiceName(ServiceDeclaration declaration) {
        return new Finding(
                MALFORMED,
                Severity.ERROR,
                PredictedError.SERVICE_CONFIGURATION_ERROR,
                declaration.location(),
                declaration.serviceInternalName(),
                MALFORMED_SUMMARY,
                "A service configuration file is named after the service type it registers providers for, and"
                        + " this file name is not a binary class name at all. Nothing can ask for a service by"
                        + " this name, so the providers listed here are offered to nobody, and the repackaging"
                        + " step that mangled the resource name usually mangled its lines with it.",
                List.of(new Evidence("the file name is not a binary class name: " + declaration.serviceName())),
                List.of(new Remediation("Rename the resource to the fully qualified binary name of the service"
                        + " type it registers providers for.")));
    }

    /**
     * Reports one provider this file names more than once.
     *
     * @param declaration the file holding the repeats
     * @param repeated every line naming the provider, in file order
     * @return the finding
     */
    static Finding duplicateProvider(ServiceDeclaration declaration, List<ServiceProviderEntry> repeated) {
        return new Finding(
                DUPLICATE,
                Severity.INFO,
                PredictedError.NONE,
                declaration.location(),
                repeated.get(0).internalName(),
                "duplicate service provider entry",
                "This file names one provider more than once. The runtime remembers the provider names it has"
                        + " already read, so the repeat is ignored and nothing fails at run time. It is still"
                        + " worth knowing: a provider named twice usually means one library reached the"
                        + " classpath twice, or that a repackaged bundle and its original both register it.",
                lines(repeated),
                List.of(new Remediation("Remove the repeated provider line so the file names this provider"
                        + " once.")));
    }

    private static List<Evidence> lines(List<ServiceProviderEntry> repeated) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence("the provider is first named on line " + repeated.get(0).lineNumber()));
        repeated.stream()
                .skip(1)
                .forEach(entry -> evidence.add(new Evidence("it is named again on line " + entry.lineNumber())));
        return List.copyOf(evidence);
    }
}
