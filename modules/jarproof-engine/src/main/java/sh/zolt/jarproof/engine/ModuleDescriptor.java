package sh.zolt.jarproof.engine;

import java.util.List;

/**
 * One module descriptor as the entry it was read from declares it.
 *
 * <p>Requirements arrive split in two, because the module system treats them differently: a
 * mandatory requirement has to resolve before the module will launch, while a {@code requires static}
 * requirement is a compile-time dependency the runtime is explicitly allowed not to find. Only the
 * mandatory list is worth checking against what a run supplies.
 *
 * <p>{@code exposedPackages} holds the packages the descriptor {@code exports} and the packages it
 * {@code opens} in one list, because both make the same promise about the package existing and
 * neither is weakened by naming specific reader modules. A package written by two clauses appears
 * twice, and the identical diagnostics that produces collapse when the check deduplicates.
 *
 * <p>Package names are internal prefixes and service names are internal class names, exactly as the
 * descriptor stores them, so they can be compared against the class index without conversion. The
 * module name itself is the one dotted name here, because that is what a {@code requires} clause in
 * some other descriptor spells.
 */
record ModuleDescriptor(
        String entryName,
        String moduleName,
        List<String> requiredModules,
        List<String> compileOnlyModules,
        List<String> exposedPackages,
        List<String> usedServices,
        List<ModuleProvision> provisions) {
    ModuleDescriptor {
        requiredModules = List.copyOf(requiredModules);
        compileOnlyModules = List.copyOf(compileOnlyModules);
        exposedPackages = List.copyOf(exposedPackages);
        usedServices = List.copyOf(usedServices);
        provisions = List.copyOf(provisions);
    }
}
