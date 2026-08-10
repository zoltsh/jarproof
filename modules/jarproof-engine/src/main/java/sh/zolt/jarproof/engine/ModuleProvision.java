package sh.zolt.jarproof.engine;

import java.util.List;

/**
 * One {@code provides} clause of a module descriptor: the service it registers an implementation of,
 * and the provider classes it names.
 *
 * <p>Both are JVM internal names, because that is the form the descriptor stores and the form every
 * other index in the engine is keyed by, so nothing has to be converted on the way in. Providers keep
 * the order the clause wrote them in, which is what makes a diagnostic built from them reproducible.
 */
record ModuleProvision(String serviceInternalName, List<String> providerInternalNames) {
    ModuleProvision {
        providerInternalNames = List.copyOf(providerInternalNames);
    }
}
