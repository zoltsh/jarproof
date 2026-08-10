package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which methods of one classpath can execute, how many there were to choose from, and how each one
 * was reached.
 *
 * <p>{@code declared} counts every method the runtime could reach on this classpath — the winning
 * copy of every class the platform does not already supply — so a caller can say how much of a
 * closure the analysis proved live rather than only which findings survived. The two numbers
 * together are the honest summary of a reachability run: a proportion near one means the analysis
 * proved almost nothing and is worth doubting.
 *
 * <p>{@code callers} holds one pointer per reached method: the method that first reached it. A pointer
 * is written only at the moment a method is discovered, and its discoverer was already reachable, so
 * every pointer leads strictly backwards in discovery order. The pointers therefore form a forest
 * rooted at the entry surface, which is why {@link #chain} needs no depth ceiling to terminate. That
 * chain is what makes a surviving finding arguable — a reader can see which first-party method starts
 * the path — and it is the first thing anyone asks when a finding they believed was dead turns out to
 * be reachable.
 */
record ReachableMethods(Set<ReachableNode> reached, Map<ReachableNode, ReachableNode> callers, int declared) {
    private static final String CHAIN_STEP = " -> ";

    ReachableMethods {
        reached = Set.copyOf(reached);
        callers = Map.copyOf(callers);
    }

    /**
     * Returns whether a reference written in one method of one class can execute.
     *
     * @param internalName the class the reference was written in
     * @param referencingMethod that method's name joined to its descriptor
     * @return whether the call graph reaches it
     */
    boolean reaches(String internalName, String referencingMethod) {
        return reached.contains(new ReachableNode(internalName, referencingMethod));
    }

    /**
     * Rebuilds the path that reached one method, the entry method first.
     *
     * <p>A method the analysis never reached answers with itself alone, which is the truthful answer
     * to why it is not in the graph.
     *
     * @param internalName the class the method belongs to
     * @param referencingMethod that method's name joined to its descriptor
     * @return the chain, each step spelled as a member and separated by an arrow
     */
    String chain(String internalName, String referencingMethod) {
        List<String> steps = new ArrayList<>();
        ReachableNode current = new ReachableNode(internalName, referencingMethod);
        steps.add(current.toString());
        while (callers.containsKey(current)) {
            current = callers.get(current);
            steps.add(current.toString());
        }
        Collections.reverse(steps);
        return String.join(CHAIN_STEP, steps);
    }
}
