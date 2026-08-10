package sh.zolt.jarproof.engine;

/**
 * One node of the call graph: the class that declares a method, and the method itself.
 *
 * <p>{@code signature} is the method name joined to its descriptor, which is exactly how the
 * reference index spells the method a reference was written in, so a reference and the node that owns
 * it compare without any parsing. The descriptor is part of the identity rather than decoration:
 * two overloads share a name and the JVM treats them as unrelated methods, so a call graph that
 * merged them would report a break in a method nothing calls.
 *
 * <p>{@code owner} is the internal name a reference would resolve to, so it names the copy of the
 * class the runtime reaches rather than any shadowed copy of the same name. Nodes are ordered by
 * class and then by signature, which is what lets a worklist be drained in one fixed order and makes
 * every chain this analysis reconstructs the same on every machine.
 */
record ReachableNode(String owner, String signature) implements Comparable<ReachableNode> {
    @Override
    public int compareTo(ReachableNode other) {
        int byOwner = owner.compareTo(other.owner);
        return byOwner != 0 ? byOwner : signature.compareTo(other.signature);
    }

    /**
     * Spells the node with {@link LinkageEvidence}'s own canonical member spelling, so a chain in a
     * finding's evidence and the subject of that finding cannot drift apart.
     */
    @Override
    public String toString() {
        return LinkageEvidence.member(owner, signature);
    }
}
