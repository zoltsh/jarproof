package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * How a reconstructed call chain reads, and what happens when it is too long to read at all.
 *
 * <p>A chain is the sequence of members {@link Reachability} walked to arrive at one method, the entry
 * method first, and it is rendered as those members joined by an arrow. Nothing here decides which
 * members are in the chain — {@link ReachableMethods} reconstructs that from the caller pointers — and
 * nothing here spells a member either, because {@link LinkageEvidence} already owns that spelling.
 *
 * <p>Long chains are shortened in the middle. A twelve-step chain still reads as a path, and a
 * hundred-step chain through a framework's internals reads as a wall of text that hides both ends: the
 * first-party method that starts it and the broken method that ends it are exactly the two facts a
 * reader needs, and they are the two facts an unshortened chain buries. So a chain past the ceiling
 * renders its first five members, a note counting the members left out, and its last five. The count
 * is the honest part of the shortening — it says how much was dropped rather than hiding that anything
 * was, and a reader who needs the whole path can widen the scope of what they read instead.
 *
 * <p>The shortening is arithmetic on a list, so it is the same everywhere the same chain is: there is
 * no clock, no path, and no hashing anywhere in this rendering.
 */
final class ReachableChain {
    /**
     * The longest chain that renders whole. Above this the middle is elided, which leaves ten members
     * plus one note — still shorter than the ceiling, so the rendering always shortens rather than
     * merely rearranges.
     */
    private static final int LONGEST_WHOLE_CHAIN = 12;

    /** How many members survive at each end of a shortened chain. */
    private static final int KEPT_AT_EACH_END = 5;

    private static final String STEP_SEPARATOR = " -> ";
    private static final String ELIDED_PREFIX = "... ";
    private static final String ELIDED_SUFFIX = " hops ...";

    private ReachableChain() {
    }

    /**
     * Renders one chain, shortening the middle when it is longer than a reader can follow.
     *
     * @param steps the members of the chain, the entry method first and the reached method last
     * @return the chain as one line
     */
    static String render(List<String> steps) {
        if (steps.size() <= LONGEST_WHOLE_CHAIN) {
            return String.join(STEP_SEPARATOR, steps);
        }
        List<String> shortened = new ArrayList<>(steps.subList(0, KEPT_AT_EACH_END));
        shortened.add(ELIDED_PREFIX + (steps.size() - 2 * KEPT_AT_EACH_END) + ELIDED_SUFFIX);
        shortened.addAll(steps.subList(steps.size() - KEPT_AT_EACH_END, steps.size()));
        return String.join(STEP_SEPARATOR, shortened);
    }
}
