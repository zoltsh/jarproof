package sh.zolt.jarproof.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Answers what a class internal name means on one classpath, and walks the hierarchy above it.
 *
 * <p>Lookup is parent-first, which is what the launcher does: the target runtime answers first, and
 * only a name it does not know reaches the classpath. A classpath copy of a name the runtime already
 * supplies is therefore invisible, silently, with no diagnostic — pretending otherwise would report a
 * failure that cannot happen. Within the classpath the winning declaration answers, even for a class
 * asking about a name its own artifact declares: a shadowed self-reference is exactly the hazard this
 * analysis exists to find.
 *
 * <p>A name the classpath claims with bytes no parser accepted resolves to nothing here, and
 * {@link #claimedWithoutShape(String)} says so, so a caller can stay quiet about it instead of
 * reporting a missing class that is present but unreadable. That entry already has its own diagnostic.
 *
 * <p>Hierarchy walks cross the boundary in the one direction that exists: a classpath class routinely
 * extends or implements a runtime type, and a runtime type never extends a classpath class. Both walks
 * stop at a depth ceiling, because hand-written bytecode can describe a hierarchy that loops, and an
 * analysis that hangs is worse than one that answers narrowly.
 */
final class ResolutionTable {
    private static final int MAXIMUM_HIERARCHY_DEPTH = 256;

    private final ArtifactCatalog catalog;
    private final JdkSymbolCatalog platform;
    private final Map<String, Optional<ResolvedClass>> resolved = new HashMap<>();

    ResolutionTable(ArtifactCatalog catalog, JdkSymbolCatalog platform) {
        this.catalog = catalog;
        this.platform = platform;
    }

    /**
     * Resolves one class internal name, runtime first and classpath second.
     *
     * @param internalName the name a reference used
     * @return the resolved class, or empty when nothing declares it in a readable form
     */
    Optional<ResolvedClass> find(String internalName) {
        return resolved.computeIfAbsent(internalName, this::lookUp);
    }

    /** Returns whether the classpath claims this name with bytes that no parser accepted. */
    boolean claimedWithoutShape(String internalName) {
        return catalog.winner(internalName)
                .filter(claim -> claim.declared().shape().isEmpty())
                .isPresent();
    }

    /**
     * Returns a class and every superclass above it, nearest first.
     *
     * <p>The walk stops where resolution stops, so a hierarchy rooted in a class nothing declares
     * still yields the part that is known rather than nothing at all.
     *
     * @param start the class to walk up from
     * @return the class itself followed by its resolvable superclasses
     */
    List<ResolvedClass> classChain(ResolvedClass start) {
        List<ResolvedClass> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Optional<ResolvedClass> current = Optional.of(start);
        while (current.isPresent() && chain.size() < MAXIMUM_HIERARCHY_DEPTH) {
            ResolvedClass here = current.get();
            if (!visited.add(here.internalName())) {
                return List.copyOf(chain);
            }
            chain.add(here);
            current = here.shape().superInternalName().flatMap(this::find);
        }
        return List.copyOf(chain);
    }

    /**
     * Returns every superinterface of a chain of classes, transitively, in declared order.
     *
     * <p>Declared order is preserved because it is what decides which inherited default method is
     * maximally specific. Existence questions do not need that order, but the answer is cheap to keep
     * honest and expensive to reconstruct later.
     *
     * @param chain classes whose interfaces to collect
     * @return the transitive superinterfaces, each appearing once
     */
    List<ResolvedClass> interfaceClosure(List<ResolvedClass> chain) {
        List<ResolvedClass> closure = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        chain.forEach(type -> pending.addAll(type.shape().interfaceInternalNames()));
        while (!pending.isEmpty() && closure.size() < MAXIMUM_HIERARCHY_DEPTH) {
            String name = pending.removeFirst();
            if (visited.add(name)) {
                find(name).ifPresent(found -> {
                    closure.add(found);
                    pending.addAll(found.shape().interfaceInternalNames());
                });
            }
        }
        return List.copyOf(closure);
    }

    private Optional<ResolvedClass> lookUp(String internalName) {
        Optional<ResolvedClass> supplied = platform.classShape(internalName).map(shape -> ResolvedClass.ofPlatform(
                internalName, shape, platform.owningModule(internalName).orElse(internalName)));
        if (supplied.isPresent()) {
            return supplied;
        }
        return catalog.winner(internalName).flatMap(claim -> claim.declared().shape()
                .map(shape -> ResolvedClass.ofClasspath(internalName, shape, claim)));
    }
}
