package sh.zolt.jarproof.engine;

import java.util.Optional;
import java.util.Set;

/**
 * What one reference does to a call graph: which method it runs, whether it allocates, and whether it
 * forces a class to initialize.
 *
 * <p>Resolution is never re-implemented here. The method an invocation runs is whatever
 * {@link ResolutionRules} says the JVM would find, walked with the same {@link ResolutionTable} every
 * linkage question uses, so the call graph and the findings can never disagree about which
 * declaration a reference means. The only thing this adds is a starting type: a direct invocation
 * starts at the class the reference names, and a dispatched invocation starts at the receiver's own
 * type, which is how one virtual call site yields one target per receiver the run can allocate.
 *
 * <p>A target the target runtime supplies is dropped rather than returned. Platform internals are
 * healthy by assumption, so an edge into them terminates the traversal instead of pulling the whole
 * runtime into the graph.
 */
final class ReachableTargets {
    /**
     * The class initializer, its name and descriptor together. The JVM reserves exactly two member
     * names and writes both inside angle brackets, so a bracketed name this signature does not begin
     * with is the other one: a constructor.
     */
    private static final String INITIALIZER_SIGNATURE = "<clinit>()V";

    private static final char RESERVED_NAME_MARK = '<';
    private static final char ARRAY_OWNER_MARK = '[';
    private static final Set<ReferenceKind> INITIALIZING = Set.of(
            ReferenceKind.GET_STATIC, ReferenceKind.PUT_STATIC, ReferenceKind.INVOKE_STATIC);
    private static final Set<ReferenceKind> DIRECT = Set.of(
            ReferenceKind.INVOKE_STATIC, ReferenceKind.INVOKE_SPECIAL, ReferenceKind.METHOD_HANDLE);
    private static final Set<ReferenceKind> DISPATCHED = Set.of(
            ReferenceKind.INVOKE_VIRTUAL, ReferenceKind.INVOKE_INTERFACE);

    private ReachableTargets() {
    }

    /**
     * Returns whether a reference names a member of a real class rather than of an array type.
     *
     * <p>An array's members are the JVM's own, so an array owner reaches no bytecode and moves no
     * call graph.
     */
    static boolean namesAClass(MemberReference reference) {
        return reference.ownerInternalName().charAt(0) != ARRAY_OWNER_MARK;
    }

    /** Returns whether reaching this reference forces the named class to be initialized. */
    static boolean initializes(ReferenceKind kind) {
        return INITIALIZING.contains(kind);
    }

    /** Returns whether this reference runs the declaration resolution finds, with no dispatch. */
    static boolean invokesDirectly(ReferenceKind kind) {
        return DIRECT.contains(kind);
    }

    /** Returns whether this reference picks its method from the receiver at run time. */
    static boolean dispatches(ReferenceKind kind) {
        return DISPATCHED.contains(kind);
    }

    /** Returns a reference's method name joined to its descriptor, which is how a node names one. */
    static String signature(MemberReference reference) {
        return reference.name() + reference.descriptor();
    }

    /** Returns a declared member's name joined to its descriptor. */
    static String signature(MemberShape member) {
        return member.name() + member.descriptor();
    }

    /**
     * Returns whether a reference brings a new object of its owner into existence.
     *
     * <p>A constructor reference allocates everywhere except inside another constructor of the same
     * object: {@code super(...)} and {@code this(...)} continue an allocation somebody else already
     * made. Counting those would put every abstract base class in the allocated set and widen every
     * virtual call above it to overrides no receiver of this run can have — which is precisely the
     * imprecision rapid type analysis exists to avoid. A class initializer allocating its own type is
     * a real allocation and is not chaining, which is how a singleton held in a static field is seen.
     *
     * @param table the resolution table, which knows what the referencing class extends
     * @param caller the reachable method the reference is written in
     * @param reference the reference as the bytecode wrote it
     * @return whether the owner joins the allocated set
     */
    static boolean allocates(ResolutionTable table, ReachableNode caller, MemberReference reference) {
        if (!constructs(reference.name())) {
            return false;
        }
        return !chains(table, caller, reference.ownerInternalName());
    }

    /**
     * Returns the class initializer of one type, when it declares one.
     *
     * @param type the resolved class being initialized
     * @return its initializer node, or empty when the class has no static setup to run
     */
    static Optional<ReachableNode> initializer(ResolvedClass type) {
        return type.shape().members().stream()
                .filter(member -> INITIALIZER_SIGNATURE.equals(signature(member)))
                .findFirst()
                .map(declared -> new ReachableNode(type.internalName(), INITIALIZER_SIGNATURE));
    }

    /**
     * Returns the method an invocation runs when it begins looking at one type.
     *
     * @param table the resolution table to walk with
     * @param typeName the class the search starts at: the named owner, or a receiver's own type
     * @param reference the reference whose name and descriptor have to be satisfied
     * @return the node that would run, or empty when nothing on the classpath would
     */
    static Optional<ReachableNode> running(
            ResolutionTable table, String typeName, MemberReference reference) {
        if (!ResolutionRules.namesMethod(reference.descriptor())) {
            return Optional.empty();
        }
        return table.find(typeName)
                .flatMap(type -> declaration(table, type, reference))
                .filter(found -> !found.owner().fromPlatform())
                .map(found -> new ReachableNode(found.owner().internalName(), signature(reference)));
    }

    private static Optional<ResolvedMember> declaration(
            ResolutionTable table, ResolvedClass type, MemberReference reference) {
        if (type.isInterface()) {
            return ResolutionRules.throughInterface(table, type, reference);
        }
        return ResolutionRules.throughHierarchy(table, type, reference);
    }

    /** Returns whether a member name or whole signature names a constructor. */
    private static boolean constructs(String member) {
        return member.charAt(0) == RESERVED_NAME_MARK && !INITIALIZER_SIGNATURE.startsWith(member);
    }

    private static boolean chains(ResolutionTable table, ReachableNode caller, String allocated) {
        if (!constructs(caller.signature())) {
            return false;
        }
        if (caller.owner().equals(allocated)) {
            return true;
        }
        return table.find(caller.owner())
                .flatMap(type -> type.shape().superInternalName())
                .filter(allocated::equals)
                .isPresent();
    }
}
