package sh.zolt.jarproof.engine;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where the JVM looks for the field or method a reference names, per JVMS §5.4.3.
 *
 * <p>Three procedures, and which one applies is decided by the reference rather than by what happens
 * to be found. Field resolution (§5.4.3.2) searches the named class, then its superinterfaces, then
 * upwards; method resolution against a class (§5.4.3.3) searches the named class, then the superclass
 * chain, then the superinterfaces; interface method resolution (§5.4.3.4) searches the named interface,
 * then {@code java/lang/Object}, then the superinterfaces — that middle step is why calling
 * {@code toString} through an interface links at all.
 *
 * <p>Field resolution and class method resolution visit the same declarations in different orders, and
 * this class answers both from one search because the question here is only whether a matching
 * declaration exists anywhere the JVM would look. Two JVMS refinements are deliberately not modelled,
 * because both can only turn a reference that links into a reported failure. Maximally-specific
 * selection among competing interface defaults decides which implementation runs, not whether the
 * method resolves. And §5.4.3.3 excludes a private or static superinterface method from satisfying a
 * class-owner reference, which this search accepts; a call site compiled against such a declaration is
 * vanishingly rare next to the cost of inventing a failure that the JVM does not raise.
 */
final class ResolutionRules {
    private static final String OBJECT = "java/lang/Object";
    private static final char METHOD_DESCRIPTOR_START = '(';

    private ResolutionRules() {
    }

    /** Returns whether a member descriptor names a method rather than a field. */
    static boolean namesMethod(String descriptor) {
        return descriptor.charAt(0) == METHOD_DESCRIPTOR_START;
    }

    /**
     * Searches a named class and everything above it, which is JVMS §5.4.3.2 and §5.4.3.3.
     *
     * @param table the resolution table to walk with
     * @param owner the class the reference named
     * @param reference the field or method reference to satisfy
     * @return the declaration that satisfies it, or empty when nothing does
     */
    static Optional<ResolvedMember> throughHierarchy(
            ResolutionTable table, ResolvedClass owner, MemberReference reference) {
        List<ResolvedClass> chain = table.classChain(owner);
        return search(joined(chain, table.interfaceClosure(chain)), reference);
    }

    /**
     * Searches a named interface, then {@code java/lang/Object}, then its superinterfaces, which is
     * JVMS §5.4.3.4.
     *
     * <p>The {@code Object} step accepts only a public instance method, exactly as the specification
     * requires, so a protected method such as {@code clone} does not quietly satisfy an interface call.
     *
     * @param table the resolution table to walk with
     * @param owner the interface the reference named
     * @param reference the method reference to satisfy
     * @return the declaration that satisfies it, or empty when nothing does
     */
    static Optional<ResolvedMember> throughInterface(
            ResolutionTable table, ResolvedClass owner, MemberReference reference) {
        Optional<ResolvedMember> declared = search(List.of(owner), reference);
        if (declared.isPresent()) {
            return declared;
        }
        Optional<ResolvedMember> inherited = table.find(OBJECT)
                .flatMap(object -> search(List.of(object), reference))
                .filter(found -> found.isPublic() && !found.isStatic());
        if (inherited.isPresent()) {
            return inherited;
        }
        return search(table.interfaceClosure(List.of(owner)), reference);
    }

    private static Optional<ResolvedMember> search(List<ResolvedClass> candidates, MemberReference reference) {
        for (ResolvedClass candidate : candidates) {
            Optional<MemberShape> declared = candidate.declared(reference.name(), reference.descriptor());
            if (declared.isPresent()) {
                return Optional.of(new ResolvedMember(candidate, declared.get()));
            }
        }
        return Optional.empty();
    }

    private static List<ResolvedClass> joined(List<ResolvedClass> first, List<ResolvedClass> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
