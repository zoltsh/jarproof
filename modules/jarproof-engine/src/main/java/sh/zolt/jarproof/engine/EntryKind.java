package sh.zolt.jarproof.engine;

/** How one effective classpath entry stores its class files. */
enum EntryKind {
    /** A JAR or other ZIP archive read entry by entry. */
    ARCHIVE,

    /** A directory of class files, addressed by relative path. */
    DIRECTORY
}
