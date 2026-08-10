package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The types a run can allocate, indexed by every type name a virtual call could reach them through.
 *
 * <p>This is the reverse of the class hierarchy, and it is the one structure that separates rapid type
 * analysis from class hierarchy analysis. A hierarchy index asks which types <em>could</em> implement
 * a method and answers with every subtype on the classpath, which promotes every optional integration
 * in a widely-implemented interface to reachable. This index answers only with types some reachable
 * code actually allocates, so a virtual call reaches an override when a receiver of that shape can
 * exist and not merely because the code was compiled.
 *
 * <p>The index is keyed by supertype rather than walked downwards on demand: a call on a type near the
 * root of a real hierarchy has thousands of descendants and a handful of allocated ones, so asking
 * "which allocated types answer to this name" is the cheap direction. Both the set and the index grow
 * and never shrink, which is what makes the fixpoint terminate. Keys and members are sorted, so
 * iterating the index is stable.
 */
final class ReachableTypes {
    private final Map<String, Set<String>> allocatedBySupertype = new TreeMap<>();
    private final Set<String> allocated = new TreeSet<>();

    /**
     * Joins one type to the allocated set and indexes it under every name it answers to.
     *
     * @param internalName the type some reachable code allocates
     * @param supertypeNames every name a reference could reach it through, including its own
     * @return whether this is the first time the type was allocated
     */
    boolean join(String internalName, List<String> supertypeNames) {
        if (!allocated.add(internalName)) {
            return false;
        }
        for (String supertype : supertypeNames) {
            allocatedBySupertype.computeIfAbsent(supertype, name -> new TreeSet<>()).add(internalName);
        }
        return true;
    }

    /**
     * Returns the allocated types a reference naming one type may dispatch to.
     *
     * @param internalName the type the reference named
     * @return the allocated types that answer to it, in ascending name order
     */
    Set<String> dispatchableFrom(String internalName) {
        return allocatedBySupertype.getOrDefault(internalName, Set.of());
    }

    /**
     * Returns every type name one class answers to: itself, the classes above it, and every interface
     * any of them declares.
     *
     * <p>The walk is the resolution table's own, which is what keeps a hierarchy that loops or that
     * ends in a class nothing declares from being this analysis's problem.
     *
     * @param table the resolution table to walk with
     * @param type the allocated class
     * @return the names, the class itself first
     */
    static List<String> namesOf(ResolutionTable table, ResolvedClass type) {
        List<ResolvedClass> chain = table.classChain(type);
        List<String> names = new ArrayList<>();
        chain.forEach(above -> names.add(above.internalName()));
        table.interfaceClosure(chain).forEach(implemented -> names.add(implemented.internalName()));
        return List.copyOf(names);
    }
}
