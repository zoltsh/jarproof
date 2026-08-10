package sh.zolt.jarproof.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Answers the two type questions a service configuration file raises: does this name exist on the
 * classpath at all, and does the class it names carry the service type.
 *
 * <p>A name is resolved the way the launcher would reach it: the target platform first, then the
 * classpath copy that wins. A provider routinely extends or implements a platform type, so the
 * supertype walk crosses between the two catalogs freely, following the superclass chain and every
 * superinterface. Each name is visited once, so a hierarchy that loops back on itself cannot spin.
 */
final class ServiceTypeHierarchy {
    private final ArtifactCatalog catalog;
    private final JdkSymbolCatalog platform;

    ServiceTypeHierarchy(ArtifactCatalog catalog, JdkSymbolCatalog platform) {
        this.catalog = catalog;
        this.platform = platform;
    }

    /** Returns whether the platform or the classpath declares this class at all. */
    boolean declares(String internalName) {
        return platform.classShape(internalName).isPresent() || catalog.winner(internalName).isPresent();
    }

    /**
     * Returns the declared shape of a class, platform first.
     *
     * @param internalName class internal name
     * @return the shape, or empty when nothing declares the class or no parser accepted its bytes
     */
    Optional<ClassShape> shape(String internalName) {
        Optional<ClassShape> declared = platform.classShape(internalName);
        return declared.isPresent()
                ? declared
                : catalog.winner(internalName).flatMap(found -> found.declared().shape());
    }

    /**
     * Returns whether a provider provably lacks a service type.
     *
     * <p>Proof requires the whole walk to succeed: every supertype the provider reaches has to
     * resolve, and none of them may be the service. A supertype nothing declares makes the answer
     * unknowable instead of negative, because an optional dependency absent from this classpath could
     * be the very type that carries the service, and Jarproof stays quiet rather than inventing a
     * failure.
     *
     * @param providerInternalName internal name of the provider class
     * @param serviceInternalName internal name of the service type
     * @return whether the provider is proven not to be a subtype of the service
     */
    boolean provablyLacks(String providerInternalName, String serviceInternalName) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(providerInternalName);
        boolean resolved = true;
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(serviceInternalName)) {
                return false;
            }
            if (visited.add(current)) {
                resolved &= addSupertypes(pending, current);
            }
        }
        return resolved;
    }

    private boolean addSupertypes(Deque<String> pending, String internalName) {
        Optional<ClassShape> declared = shape(internalName);
        declared.ifPresent(shape -> {
            shape.superInternalName().ifPresent(pending::add);
            pending.addAll(shape.interfaceInternalNames());
        });
        return declared.isPresent();
    }
}
