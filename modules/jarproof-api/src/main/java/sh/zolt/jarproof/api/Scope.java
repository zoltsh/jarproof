package sh.zolt.jarproof.api;

/** Controls which bytecode origins may produce linkage findings. */
public enum Scope {
    /** Report errors from application bytecode; downgrade library-only evidence. */
    APPLICATION,

    /** Analyze every bytecode origin without implying executable reachability. */
    ALL
}
