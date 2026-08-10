package sh.zolt.jarproof.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import sh.zolt.jarproof.api.Scope;

/**
 * Which methods of one classpath a static call graph proves executable, which is what
 * {@link Scope#REACHABLE} means and the whole of what it promises.
 *
 * <h2>What the mode reports</h2>
 *
 * <p>{@code REACHABLE} is the strictest and quietest mode. It reports only linkage findings whose
 * referencing method this analysis reaches, every one of them at error severity — application and
 * library origin alike, because a proven static chain from first-party code deserves the same
 * confidence wherever the broken reference is written. A linkage finding in a method nothing reaches
 * is omitted entirely rather than downgraded, which is the difference between this mode and
 * {@link Scope#ALL}. The classpath, bytecode-level, service, and module families are facts about
 * artifacts rather than about executable paths, so no scope touches them and this analysis never sees
 * them.
 *
 * <h2>The entry surface</h2>
 *
 * <p>Every method every application-origin class declares is reachable to begin with: constructors,
 * class initializers, and private methods included. The user shipped that bytecode as first-party, and
 * frameworks and reflection invoke first-party code in ways no static analysis can see, so the whole
 * application surface is presumed live rather than proved live. Precision is spent on libraries
 * instead, because that is where DESIGN section 4's false-positive problem lives. The consequence is
 * worth stating plainly: at this scope the application-origin findings are exactly the ones
 * {@link Scope#APPLICATION} reports, and the new information is which library findings join them.
 * Every application-origin class is likewise allocated a priori, for the same reason.
 *
 * <h2>The graph</h2>
 *
 * <p>Rapid type analysis, deliberately, rather than class hierarchy analysis. CHA asks which types
 * could implement a method and answers with every subtype on the classpath, which would promote half
 * of every optional integration to reachable and make this mode as loud as {@code ALL}. RTA asks
 * which types some reachable code can actually allocate, and dispatches only into those.
 *
 * <ul>
 *   <li>A static or special invocation is a direct edge to whatever declaration {@link ResolutionRules}
 *       finds, which is the same parent-first resolution every linkage question uses.
 *   <li>A virtual or interface invocation edges to the resolved declaration and to the method each
 *       allocated receiver would run, found by resolving the same name and descriptor from that
 *       receiver's own type.
 *   <li>A type joins the allocated set when reachable code invokes its constructor. A constructor
 *       chaining to {@code super} or {@code this} allocates nothing new and is not counted.
 *   <li>A class initializes when it is allocated or when a reachable reference names one of its static
 *       members; its initializer and every initializer above it become reachable, in the order the JVM
 *       runs them.
 *   <li>A constant-pool method handle — an {@code ldc} handle, or a bootstrap argument naming a lambda
 *       body — is a direct edge to the method it names, and allocates its owner when it names a
 *       constructor. This over-approximates on purpose: a lambda body counts as reachable once its
 *       capture site is, whether or not the functional interface is ever invoked.
 *   <li>Edges into the target runtime terminate. Platform internals are healthy by assumption, and a
 *       callback the runtime makes back into library code — a comparator's {@code compare}, a map
 *       key's {@code hashCode} — is not traversed. That is the standard soundness gap of static
 *       reachability without platform bytecode, and DESIGN section 4 names it rather than hiding it.
 *   <li>Edges out of a linkage probe terminate as well. A method whose own bytecode names
 *       {@code LinkageError} or a subclass of it has declared that a reference beneath it may fail to
 *       resolve, and {@link ReachableGuard} explains why that declaration is worth honouring: without it
 *       an optional integration a library deliberately absorbs is reachable, executed, and broken all at
 *       once, which is true and useless. The probe's own references are still judged.
 * </ul>
 *
 * <p>Both sets grow together and neither shrinks, so the worklist reaches a fixpoint bounded by the
 * method count of the classpath. There is no recursion: every edge defers its target to the worklist,
 * which is drained in ascending node order, so the graph and every chain reconstructed from it are the
 * same on every machine.
 */
final class Reachability {
    private final ResolutionTable table;
    private final ReachableBytecode bytecode;
    private final ReachableTypes allocated = new ReachableTypes();
    private final Set<ReachableNode> reached = new HashSet<>();
    private final Map<ReachableNode, ReachableNode> callers = new HashMap<>();
    private final TreeSet<ReachableNode> pending = new TreeSet<>();
    private final Map<String, Map<String, Site>> sites = new TreeMap<>();

    private Reachability(ArtifactCatalog catalog, ResolutionTable table) {
        this.table = table;
        this.bytecode = ReachableBytecode.of(catalog, table);
    }

    /**
     * Builds the call graph of one classpath and returns what it proved.
     *
     * @param catalog the read classpath
     * @param table the resolution table the linkage findings are judged with, reused so the graph and
     *     the findings can never disagree about which declaration a reference means
     * @return the reachable methods, with a caller pointer for each
     */
    static ReachableMethods of(ArtifactCatalog catalog, ResolutionTable table) {
        Reachability analysis = new Reachability(catalog, table);
        analysis.enterApplicationSurface();
        analysis.settle();
        return new ReachableMethods(
                analysis.reached, analysis.callers, analysis.bytecode.declaredMethods());
    }

    /** Enters every first-party method, then allocates every first-party type. */
    private void enterApplicationSurface() {
        bytecode.applicationMethods().forEach(this::enter);
        bytecode.applicationTypes().forEach(this::allocateFirstParty);
    }

    /**
     * Allocates one application type without a caller. Every method it declares is already an entry,
     * and no call site has been expanded yet, so this only seeds the allocated set.
     */
    private void allocateFirstParty(String internalName) {
        classpathType(internalName).ifPresent(
                type -> allocated.join(internalName, ReachableTypes.namesOf(table, type)));
    }

    /** Drains the worklist in ascending node order until neither set can grow. */
    private void settle() {
        while (!pending.isEmpty()) {
            expand(pending.pollFirst());
        }
    }

    /**
     * Follows every edge out of one reachable method, unless that method is a linkage probe.
     *
     * <p>A probe's own references are still judged — it is reachable, and what it names is resolved like
     * anything else. What stops is the traversal beneath it, because its author wrote a handler saying a
     * reference down there may fail to resolve. {@link ReachableGuard} explains why that is the one place
     * this analysis is allowed to be quieter than the truth.
     */
    private void expand(ReachableNode caller) {
        if (bytecode.absorbsLinkageFailure(caller)) {
            return;
        }
        for (MemberReference reference : bytecode.references(caller)) {
            expand(caller, reference);
        }
    }

    private void expand(ReachableNode caller, MemberReference reference) {
        if (!ReachableTargets.namesAClass(reference)) {
            return;
        }
        if (ReachableTargets.initializes(reference.kind())) {
            initialize(caller, reference.ownerInternalName());
        }
        if (ReachableTargets.allocates(table, caller, reference)) {
            allocate(caller, reference.ownerInternalName());
        }
        if (ReachableTargets.invokesDirectly(reference.kind())) {
            ReachableTargets.running(table, reference.ownerInternalName(), reference)
                    .ifPresent(target -> reach(caller, target));
        }
        if (ReachableTargets.dispatches(reference.kind())) {
            dispatch(caller, reference);
        }
    }

    /**
     * Records a virtual call site and edges into every receiver the run can already allocate.
     *
     * <p>The site is kept because the allocated set is still growing: a type allocated later has to
     * find the call sites it can now answer, and it finds them here rather than by re-expanding every
     * reachable method.
     */
    private void dispatch(ReachableNode caller, MemberReference reference) {
        String owner = reference.ownerInternalName();
        sites.computeIfAbsent(owner, name -> new TreeMap<>())
                .putIfAbsent(ReachableTargets.signature(reference), new Site(caller, reference));
        ReachableTargets.running(table, owner, reference).ifPresent(target -> reach(caller, target));
        for (String receiver : allocated.dispatchableFrom(owner)) {
            ReachableTargets.running(table, receiver, reference)
                    .ifPresent(target -> reach(caller, target));
        }
    }

    private void allocate(ReachableNode caller, String internalName) {
        Optional<ResolvedClass> type = classpathType(internalName);
        if (type.isEmpty()) {
            return;
        }
        List<String> names = ReachableTypes.namesOf(table, type.get());
        if (!allocated.join(internalName, names)) {
            return;
        }
        initialize(caller, internalName);
        for (String supertype : names) {
            dispatchInto(internalName, supertype);
        }
    }

    /** Answers the call sites one freshly allocated receiver can now reach. */
    private void dispatchInto(String receiver, String supertype) {
        for (Site site : sites.getOrDefault(supertype, Map.of()).values()) {
            ReachableTargets.running(table, receiver, site.reference())
                    .ifPresent(target -> reach(site.caller(), target));
        }
    }

    /** Reaches the initializer of one class and of every class above it, as the JVM would run them. */
    private void initialize(ReachableNode caller, String internalName) {
        Optional<ResolvedClass> type = classpathType(internalName);
        if (type.isEmpty()) {
            return;
        }
        for (ResolvedClass above : table.classChain(type.get())) {
            if (!above.fromPlatform()) {
                ReachableTargets.initializer(above).ifPresent(node -> reach(caller, node));
            }
        }
    }

    private Optional<ResolvedClass> classpathType(String internalName) {
        return table.find(internalName).filter(type -> !type.fromPlatform());
    }

    /** Enters an entry method, which by construction is named once and has no caller to record. */
    private void enter(ReachableNode node) {
        reached.add(node);
        pending.add(node);
    }

    private void reach(ReachableNode caller, ReachableNode target) {
        if (!reached.add(target)) {
            return;
        }
        callers.put(target, caller);
        pending.add(target);
    }

    /**
     * One virtual call site: the reachable method that wrote it, and the reference it wrote.
     *
     * <p>Both halves are needed later. The reference supplies the name and descriptor a new receiver
     * has to satisfy, and the caller is what a reconstructed chain has to name — an override unlocked
     * by an allocation elsewhere was still reached from the call site, not from the allocation.
     */
    private record Site(ReachableNode caller, MemberReference reference) {
    }
}
