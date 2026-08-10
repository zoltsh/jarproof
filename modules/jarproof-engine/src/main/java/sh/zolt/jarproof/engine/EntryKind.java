package sh.zolt.jarproof.engine;

/** How one effective classpath entry stores its class files. */
enum EntryKind {
    /** A JAR or other ZIP archive read entry by entry. */
    ARCHIVE,

    /** A directory of class files, addressed by relative path. */
    DIRECTORY,

    /** The classes root of an application archive that carries its own dependencies. */
    NESTED_CLASSES,

    /** A library archive stored inside an application archive, read from that archive's bytes. */
    NESTED_ARCHIVE,

    /** An expanded application archive, read for the launcher classes at its own top level. */
    HOST_ARCHIVE
}
