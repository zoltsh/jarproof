package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The classpath as reachability reads it: the references each executable method makes, which methods
 * are first-party, and how many methods there were to begin with.
 *
 * <p>Only the winning declaration of each class name is read, and only when the target runtime does
 * not already supply that name, because those are exactly the bytes a launcher would execute. A
 * shadowed copy of a class is therefore judged by the winner's reachability: the runtime never runs
 * the shadowed copy, so asking whether its methods are reachable would answer a question about
 * bytecode nobody executes. A class whose bytes no parser accepted contributes nothing here and keeps
 * the diagnostic it already has.
 *
 * <p>References are grouped by the method they were written in, which is the grouping the reference
 * index already implies and the one a call graph needs: a method is expanded once, and every
 * reference it wrote is an edge out of it. Within a method the bytecode order is kept, so two runs
 * expand the same method in the same order.
 *
 * <p>The same pass notes which methods are linkage probes, because {@link ReachableGuard} reads exactly
 * the type references this pass is already walking. A probe is indexed like any other method — its own
 * references are judged — and the traversal simply stops there.
 */
final class ReachableBytecode {
    private final Map<ReachableNode, List<MemberReference>> referencesByMethod = new HashMap<>();
    private final Set<ReachableNode> applicationMethods = new TreeSet<>();
    private final Set<String> applicationTypes = new TreeSet<>();
    private final Set<ReachableNode> probes = new TreeSet<>();
    private final ResolutionTable table;
    private final ReachableGuard guard;
    private int declaredMethods;

    private ReachableBytecode(ResolutionTable table) {
        this.table = table;
        this.guard = new ReachableGuard(table);
    }

    /**
     * Reads one catalog.
     *
     * @param catalog the read classpath, whose declarations are already in classpath order
     * @param table the resolution table, which decides whether the runtime supplies a name first
     * @return the indexed bytecode
     */
    static ReachableBytecode of(ArtifactCatalog catalog, ResolutionTable table) {
        ReachableBytecode read = new ReachableBytecode(table);
        for (Map.Entry<String, List<ClassDeclaration>> claim : catalog.declarations().entrySet()) {
            read.index(claim.getKey(), claim.getValue().get(0));
        }
        return read;
    }

    /** Returns the references one method wrote, in bytecode order. */
    List<MemberReference> references(ReachableNode node) {
        return referencesByMethod.getOrDefault(node, List.of());
    }

    /** Returns every method every application-origin class declares, in ascending node order. */
    Set<ReachableNode> applicationMethods() {
        return applicationMethods;
    }

    /** Returns every application-origin class name, in ascending order. */
    Set<String> applicationTypes() {
        return applicationTypes;
    }

    /** Returns how many methods the runtime could reach on this classpath, reachable or not. */
    int declaredMethods() {
        return declaredMethods;
    }

    /** Returns whether this method's own bytecode declares that a reference below it may not link. */
    boolean absorbsLinkageFailure(ReachableNode node) {
        return probes.contains(node);
    }

    /**
     * Indexes one class name, or skips it.
     *
     * <p>One question settles both reasons to skip. Resolution answers with the target runtime's copy
     * of a name it supplies, and it answers with nothing at all for a classpath name whose bytes no
     * parser accepted, so a name that resolves to a classpath class is exactly a name whose readable
     * bytes the runtime would execute — and the shape it resolved to is the winner's own.
     */
    private void index(String internalName, ClassDeclaration winner) {
        Optional<ResolvedClass> executed = table.find(internalName).filter(type -> !type.fromPlatform());
        if (executed.isEmpty()) {
            return;
        }
        count(executed.get().shape());
        if (winner.artifact().origin() == ClasspathOrigin.APPLICATION) {
            enter(internalName, executed.get().shape());
        }
        record(internalName, winner.declared().references());
    }

    private void record(String internalName, ClassReferences references) {
        for (MemberReference reference : references.members()) {
            referencesByMethod
                    .computeIfAbsent(
                            new ReachableNode(internalName, reference.referencingMethod()),
                            node -> new ArrayList<>())
                    .add(reference);
        }
        for (TypeReference reference : references.types()) {
            if (guard.absorbsLinkageFailure(reference.internalName())) {
                probes.add(new ReachableNode(internalName, reference.referencingMethod()));
            }
        }
    }

    private void count(ClassShape shape) {
        for (MemberShape member : shape.members()) {
            if (ResolutionRules.namesMethod(member.descriptor())) {
                declaredMethods++;
            }
        }
    }

    private void enter(String internalName, ClassShape shape) {
        applicationTypes.add(internalName);
        for (MemberShape member : shape.members()) {
            if (ResolutionRules.namesMethod(member.descriptor())) {
                applicationMethods.add(new ReachableNode(internalName, ReachableTargets.signature(member)));
            }
        }
    }
}
