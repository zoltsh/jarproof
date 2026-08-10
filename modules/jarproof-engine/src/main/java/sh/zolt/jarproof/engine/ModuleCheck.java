package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import sh.zolt.jarproof.api.Finding;

/**
 * Verifies what every module descriptor and reserved module name on the classpath promises.
 *
 * <p>None of it describes the deployment this run models. The 0.1 model is a flat classpath with
 * application code in the unnamed module, and on a flat classpath a module descriptor is inert: the
 * launcher never reads it, a {@code requires} clause resolves nothing, an {@code exports} clause hides
 * nothing, and one package split across two artifacts merges without complaint. Any check claiming
 * otherwise would be a false positive, and none of these do.
 *
 * <p>What makes them worth reporting is that an artifact carrying a descriptor — or reserving a name
 * through an {@code Automatic-Module-Name} manifest attribute — is advertising that it can be deployed
 * on a module path, and a descriptor that cannot resolve refuses to launch there. The range is gated on
 * exactly that claim: an artifact making neither claim is a plain classpath library and is never the
 * subject of a JP5xxx finding, split packages included. Because the claim is about a deployment other
 * than the one being verified, the default severity is warning with no predicted runtime failure. The
 * provider check is the exception, since a service registration is honoured by the same
 * {@code ServiceLoader} in both worlds and fails identically in both.
 *
 * <p>Two requirements are never measured. A requirement on {@code java.base} is implicit in every
 * module, and a {@code requires static} requirement is compile-only — the module system is content to
 * leave it unresolved, so treating either as a fault would report a working artifact as broken.
 *
 * <p>The existence questions are asked of the whole run rather than of one module graph. A consumed
 * service type counts as declared when the target platform or any supplied artifact declares it, and a
 * provider's supertype walk crosses artifact boundaries freely. Readability is deliberately not
 * enforced: a type that exists but that this module cannot read is a different fault, and reporting it
 * as an absent type would name the wrong problem.
 *
 * <p>Automatic modules derived from a file name stay out of scope in this release. That derivation is
 * the JDK's own, version-suffix stripping and character replacement included, and implementing half of
 * it would invent module names nothing agrees with.
 *
 * <p>Findings come back unordered for the run to sort, and identical ones collapse: a package written
 * by both an {@code exports} and an {@code opens} clause is one fault, not two. They are produced in
 * classpath order and then in descriptor order, both properties of the input rather than of how the
 * analysis happened to run.
 */
final class ModuleCheck {
    private static final String IMPLICIT_MODULE = "java.base";

    private final List<ModuleClaim> claims;
    private final Set<String> resolvable;
    private final ServiceTypeHierarchy hierarchy;
    private final ModuleInstantiability instantiability;
    private final Set<Finding> unique = new LinkedHashSet<>();

    private ModuleCheck(ArtifactCatalog catalog, JdkSymbolCatalog platform) {
        this.claims = ModuleClaimReader.read(catalog);
        this.resolvable = resolvable(claims, platform);
        this.hierarchy = new ServiceTypeHierarchy(catalog, platform);
        this.instantiability = new ModuleInstantiability(hierarchy);
    }

    /**
     * Runs the module-descriptor checks.
     *
     * @param catalog the read classpath
     * @param platform the target platform's symbols, whose owning modules are the platform module
     *     universe a requirement can resolve against
     * @return every module finding, unordered and deduplicated
     */
    static List<Finding> run(ArtifactCatalog catalog, JdkSymbolCatalog platform) {
        ModuleCheck check = new ModuleCheck(catalog, platform);
        check.claims.forEach(check::inspect);
        check.addSplitPackages(catalog);
        return List.copyOf(check.unique);
    }

    private void inspect(ModuleClaim claim) {
        claim.automaticModuleName()
                .filter(reserved -> !ServiceBinaryName.isLegal(reserved))
                .ifPresent(reserved -> unique.add(ModuleFinding.illegalAutomaticName(claim.display(), reserved)));
        claim.descriptor().ifPresent(descriptor -> inspectDescriptor(claim, descriptor));
    }

    private void inspectDescriptor(ModuleClaim claim, ModuleDescriptor descriptor) {
        descriptor.requiredModules().stream()
                .filter(required -> !IMPLICIT_MODULE.equals(required))
                .filter(required -> !resolvable.contains(required))
                .forEach(required -> unique.add(ModuleFinding.unresolvedRequirement(claim, descriptor, required)));
        descriptor.exposedPackages().stream()
                .filter(exposed -> !claim.contributesTo(exposed))
                .forEach(exposed -> unique.add(ModulePackageFinding.emptyExposure(claim, descriptor, exposed)));
        descriptor.usedServices().stream()
                .filter(used -> !hierarchy.declares(used))
                .forEach(used -> unique.add(ModuleFinding.unresolvedService(claim, descriptor, used)));
        descriptor.provisions().forEach(provision -> inspectProvision(claim, descriptor, provision));
    }

    private void inspectProvision(ModuleClaim claim, ModuleDescriptor descriptor, ModuleProvision provision) {
        for (String provider : provision.providerInternalNames()) {
            List<String> faults = faultsOf(claim, provision.serviceInternalName(), provider);
            if (!faults.isEmpty()) {
                unique.add(ModuleProviderFinding.of(
                        claim, descriptor, provision.serviceInternalName(), provider, faults));
            }
        }
    }

    /**
     * Returns why the module system could not use one registered provider.
     *
     * <p>A provider whose bytes no parser accepted is skipped rather than guessed at, exactly as the
     * classpath service check skips one: the unreadable or too-new class file is already reported on its
     * own terms, and answering the service questions from a shape nobody has would point the reader at
     * the wrong file.
     */
    private List<String> faultsOf(ModuleClaim claim, String service, String provider) {
        Optional<IndexedClass> presented = claim.presented(provider);
        if (presented.isEmpty()) {
            return List.of(ModuleProviderFinding.OUTSIDE_MODULE);
        }
        Optional<ClassShape> shape = presented.get().shape();
        if (shape.isEmpty()) {
            return List.of();
        }
        List<String> faults = new ArrayList<>();
        if (hierarchy.declares(service) && hierarchy.provablyLacks(provider, service)) {
            faults.add(ModuleProviderFinding.WRONG_TYPE);
        }
        faults.addAll(instantiability.failures(shape.get(), service));
        return List.copyOf(faults);
    }

    private void addSplitPackages(ArtifactCatalog catalog) {
        for (Map.Entry<String, Set<String>> split : catalog.artifactsByPackage().entrySet()) {
            List<ModuleClaim> capable = moduleCapableAmong(split.getValue());
            if (capable.size() > 1) {
                unique.add(ModulePackageFinding.splitPackage(split.getKey(), capable));
            }
        }
    }

    /**
     * Returns the module-capable contributors to one package, in classpath order and at most one per
     * artifact path, so the same archive supplied twice is one contributor rather than a split package.
     */
    private List<ModuleClaim> moduleCapableAmong(Set<String> contributors) {
        List<ModuleClaim> capable = new ArrayList<>();
        for (String contributor : contributors) {
            claims.stream()
                    .filter(claim -> claim.isModuleCapable() && claim.display().equals(contributor))
                    .findFirst()
                    .ifPresent(capable::add);
        }
        return List.copyOf(capable);
    }

    private static Set<String> resolvable(List<ModuleClaim> claims, JdkSymbolCatalog platform) {
        Set<String> names = new TreeSet<>();
        claims.forEach(claim -> claim.claimedModuleName().ifPresent(names::add));
        platform.entries().forEach(entry -> names.add(entry.module()));
        return Set.copyOf(names);
    }
}
