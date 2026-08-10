package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * One class as an artifact actually presents it.
 *
 * <p>{@code entryName} is the entry the bytes came from, which for a multi-release archive is the
 * versioned path that was selected. {@code internalName} is the name the runtime looks the class up
 * by, derived from the base entry name rather than from the class file's own identity, because that
 * is what decides which copy wins. {@code shape} is absent when the bytes are present but no parser
 * accepts them, so a corrupt entry still participates in duplicate and bytecode-level reasoning.
 */
record IndexedClass(
        String entryName,
        String internalName,
        int classFileMajor,
        int classFileMinor,
        String digest,
        Optional<ClassShape> shape,
        ClassReferences references) {
    private static final int SHORT_DIGEST_LENGTH = 8;

    /** Returns the digest prefix a report shows when it has to distinguish two copies of a class. */
    String shortDigest() {
        return digest.substring(0, SHORT_DIGEST_LENGTH);
    }
}
