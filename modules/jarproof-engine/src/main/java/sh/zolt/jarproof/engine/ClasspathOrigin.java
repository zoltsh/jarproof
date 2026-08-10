package sh.zolt.jarproof.engine;

/** Why one entry is on the effective classpath, which decides whose bytecode counts as first-party. */
enum ClasspathOrigin {
    /** Supplied as an application root under test. */
    APPLICATION,

    /** Supplied as a runtime classpath entry, or inherited from a manifest chain. */
    CLASSPATH
}
