package sh.zolt.jarproof.engine;

/**
 * The bounded resources one verification run is allowed to consume.
 *
 * <p>Analysed artifacts are untrusted input, so every dimension that an attacker or an accident
 * could inflate carries a documented ceiling. Archives are read entry by entry and never extracted
 * to disk. A breached ceiling stops the run with an {@link IllegalStateException} that names the
 * limit and the input that exceeded it, because a truncated analysis would be a silent false
 * negative.
 */
final class ResourceBudget {
    /** Largest number of entries a single archive or class directory may declare. */
    static final int MAXIMUM_ARCHIVE_ENTRIES = 262144;

    /** Largest number of bytes one class file may occupy. */
    static final int MAXIMUM_CLASS_FILE_BYTES = 33554432;

    /** Largest number of bytes one run may read out of all archives together. */
    static final long MAXIMUM_EXPANDED_BYTES = 4294967296L;

    /** Largest factor by which one stored entry may expand when read. */
    static final long MAXIMUM_COMPRESSION_RATIO = 200L;

    /** Largest number of findings one run may report. */
    static final int MAXIMUM_FINDINGS = 20000;

    private long expandedBytes;

    /** Rejects an artifact that declares more entries than the ceiling allows. */
    void countArchiveEntries(int entries, String artifact) {
        if (entries > MAXIMUM_ARCHIVE_ENTRIES) {
            throw new IllegalStateException(
                    "An artifact may declare at most " + MAXIMUM_ARCHIVE_ENTRIES + " entries, exceeded by " + artifact);
        }
    }

    /** Rejects a class file larger than the ceiling allows; a negative size is simply unknown. */
    void checkClassFileBytes(long bytes, String entryName) {
        if (bytes > MAXIMUM_CLASS_FILE_BYTES) {
            throw new IllegalStateException(
                    "A class file may occupy at most " + MAXIMUM_CLASS_FILE_BYTES + " bytes, exceeded by " + entryName);
        }
    }

    /** Adds bytes read out of an archive and rejects a run that expands more than the ceiling allows. */
    void addExpandedBytes(long bytes, String entryName) {
        expandedBytes += bytes;
        if (expandedBytes > MAXIMUM_EXPANDED_BYTES) {
            throw new IllegalStateException("One run may expand at most " + MAXIMUM_EXPANDED_BYTES
                    + " bytes, exceeded while reading " + entryName);
        }
    }

    /** Rejects an entry that expands by a larger factor than the ceiling allows. */
    void checkCompressionRatio(long compressedBytes, long expandedEntryBytes, String entryName) {
        if (compressedBytes <= 0 || expandedEntryBytes <= 0) {
            return;
        }
        if (expandedEntryBytes / compressedBytes > MAXIMUM_COMPRESSION_RATIO) {
            throw new IllegalStateException("A stored entry may expand at most " + MAXIMUM_COMPRESSION_RATIO
                    + " times, exceeded by " + entryName);
        }
    }

    /** Rejects a run that produced more findings than the ceiling allows. */
    void checkFindingCount(int findings) {
        if (findings > MAXIMUM_FINDINGS) {
            throw new IllegalStateException(
                    "One run may report at most " + MAXIMUM_FINDINGS + " findings, and this run produced " + findings);
        }
    }
}
