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
 *
 * <p>{@code sourceFile} is the {@code SourceFile} attribute this entry declared, absent for a class
 * compiled without debug information. It travels with the class rather than with each reference
 * because one class file was compiled from one source file, and a report that points at a line has
 * to name the file that line is in.
 */
record IndexedClass(
        String entryName,
        String internalName,
        int classFileMajor,
        int classFileMinor,
        String digest,
        Optional<ClassShape> shape,
        ClassReferences references,
        Optional<String> sourceFile) {
    private static final int SHORT_DIGEST_LENGTH = 8;

    /** Returns the digest prefix a report shows when it has to distinguish two copies of a class. */
    String shortDigest() {
        return digest.substring(0, SHORT_DIGEST_LENGTH);
    }
}
