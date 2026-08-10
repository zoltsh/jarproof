package sh.zolt.jarproof.api;

/** Controls which bytecode a linkage finding may come from, and how much proof it needs. */
public enum Scope {
    /** Report errors from application bytecode; downgrade library-only evidence. */
    APPLICATION,

    /** Analyze every bytecode origin without implying executable reachability. */
    ALL,

    /**
     * Report only the linkage findings a method-level call graph proves executable, each as an error.
     *
     * <p>This is the strictest and the quietest setting, and the only one that claims reachability.
     * A finding survives when the method its reference is written in is reachable from application
     * bytecode through a chain of invocations, class initializations, and allocations that a static
     * analysis can follow, and that does not pass through a method whose own bytecode declares a
     * handler for a linkage failure. A finding in a method nothing reaches is omitted rather than
     * downgraded. Origin stops deciding severity here, because a proven chain from first-party code
     * is the same problem in a library as in the application.
     *
     * <p>Every method an application-origin class declares is treated as reachable to begin with,
     * so application findings match {@link #APPLICATION} exactly and the added information is which
     * library findings a chain reaches. Artifact-level families — classpath conflicts, bytecode
     * levels, services, module descriptors — are facts about artifacts rather than about executable
     * paths, and no setting of this vocabulary touches them.
     */
    REACHABLE
}
