package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sh.zolt.jarproof.api.Finding;

/**
 * Verifies what every {@code META-INF/services} file on the classpath promises.
 *
 * <p>Five questions, asked per configuration file: is the file's own name a service type name, is
 * every line a provider name, does each named provider exist, does it carry the service type, and
 * can it be constructed. Only classpath rules are applied: a provider reached through a classpath
 * needs a public no-argument constructor, and the module-path
 * {@code provider()} factory form is out of scope until the JP5xxx range ships.
 *
 * <p>Three situations are deliberately quiet, because a finding nobody can act on costs more than the
 * one it might have caught:
 *
 * <ul>
 *   <li>When the service type itself is declared nowhere, assignability is unverifiable and is
 *       skipped for that file. Nothing can ask for a service whose type is absent, so the file is
 *       inert rather than broken; the providers it lists are still checked for existence and for
 *       being constructible, since those faults outlive the missing service type.
 *   <li>When a provider is declared but no parser accepted its bytes, its shape is unknown, so the
 *       service type and constructor questions cannot be answered. The unreadable or too-new class
 *       file is already reported as JP3004 or JP3001, and repeating it here as a service fault would
 *       point the reader at the wrong file.
 *   <li>When a provider's supertype is declared nowhere, the walk cannot prove the provider lacks the
 *       service type, so no JP4002 is reported (see {@link ServiceTypeHierarchy#provablyLacks}).
 * </ul>
 *
 * <p>A duplicate registration is reported within one file only. The same provider named by two
 * artifacts is one library present twice, which the JP2xxx duplicate and version findings describe
 * with far more evidence than a services file can.
 *
 * <p>Findings come back unordered for the run to sort, and identical ones collapse: one code, one
 * subject, and one location describe one fault, however many lines produced it.
 */
final class ServiceProviderCheck {
    private final ServiceTypeHierarchy hierarchy;
    private final Map<List<String>, Finding> unique = new LinkedHashMap<>();

    private ServiceProviderCheck(ArtifactCatalog catalog, JdkSymbolCatalog platform) {
        this.hierarchy = new ServiceTypeHierarchy(catalog, platform);
    }

    /**
     * Runs the service checks, charging the configuration files it reads to a budget of their own.
     *
     * @param catalog the read classpath
     * @param platform the target platform's symbols, which providers may extend
     * @return every service finding, unordered and deduplicated
     */
    static List<Finding> run(ArtifactCatalog catalog, JdkSymbolCatalog platform) {
        return run(catalog, platform, new ResourceBudget());
    }

    /**
     * Runs the service checks against a budget the caller already owns, which is what a whole
     * verification should pass: the engine's ceilings are per run, so configuration files and class
     * files have to draw on one allowance rather than two.
     *
     * @param catalog the read classpath
     * @param platform the target platform's symbols, which providers may extend
     * @param budget the run's resource budget
     * @return every service finding, unordered and deduplicated
     */
    static List<Finding> run(ArtifactCatalog catalog, JdkSymbolCatalog platform, ResourceBudget budget) {
        ServiceProviderCheck check = new ServiceProviderCheck(catalog, platform);
        ServiceDeclarationReader.read(catalog, budget).forEach(check::inspect);
        return List.copyOf(check.unique.values());
    }

    private void inspect(ServiceDeclaration declaration) {
        if (!ServiceBinaryName.isLegal(declaration.serviceName())) {
            add(ServiceFileFinding.malformedServiceName(declaration));
        }
        declaration.providers().forEach(entry -> inspectProvider(declaration, entry));
        repeats(declaration).forEach(repeated -> add(ServiceFileFinding.duplicateProvider(declaration, repeated)));
    }

    private void inspectProvider(ServiceDeclaration declaration, ServiceProviderEntry entry) {
        if (!entry.isLegal()) {
            add(ServiceFileFinding.malformedProviderLine(declaration, entry));
            return;
        }
        if (!hierarchy.declares(entry.internalName())) {
            add(ServiceProviderFinding.missingProvider(declaration, entry));
            return;
        }
        hierarchy.shape(entry.internalName()).ifPresent(shape -> inspectShape(declaration, entry, shape));
    }

    private void inspectShape(ServiceDeclaration declaration, ServiceProviderEntry entry, ClassShape shape) {
        String service = declaration.serviceInternalName();
        if (hierarchy.declares(service) && hierarchy.provablyLacks(entry.internalName(), service)) {
            add(ServiceProviderFinding.wrongServiceType(declaration, entry));
        }
        List<String> failures = ServiceInstantiability.failures(shape);
        if (!failures.isEmpty()) {
            add(ServiceProviderFinding.notInstantiable(declaration, entry, failures));
        }
    }

    private void add(Finding finding) {
        unique.putIfAbsent(identity(finding), finding);
    }

    private static List<String> identity(Finding finding) {
        return List.of(
                finding.code().value(),
                finding.subject(),
                finding.artifact().artifact(),
                finding.artifact().classEntry().orElseThrow());
    }

    private static List<List<ServiceProviderEntry>> repeats(ServiceDeclaration declaration) {
        Map<String, List<ServiceProviderEntry>> byName = new LinkedHashMap<>();
        for (ServiceProviderEntry entry : declaration.providers()) {
            if (entry.isLegal()) {
                byName.computeIfAbsent(entry.declaredName(), absent -> new ArrayList<>()).add(entry);
            }
        }
        return byName.values().stream().filter(named -> named.size() > 1).toList();
    }
}
