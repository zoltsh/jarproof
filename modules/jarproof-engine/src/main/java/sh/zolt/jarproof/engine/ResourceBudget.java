package sh.zolt.jarproof.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The bounded resources one verification run is allowed to consume.
 *
 * <p>Analysed artifacts are untrusted input, so every dimension that an attacker or an accident
 * could inflate carries a documented ceiling. Archives are read entry by entry and never extracted
 * to disk. A breached ceiling stops the run with an {@link IllegalStateException} that names the
 * limit and the input that exceeded it, because a truncated analysis would be a silent false
 * negative.
 *
 * <p>One budget is charged by every artifact of a run, and a run reads its artifacts at the same time
 * (see {@link ScanSchedule}), so charging is safe from several threads at once. Every ceiling on a
 * single input — a class file's size, one entry's compression ratio, an artifact's entry count — reads
 * only what that input declares and is therefore as deterministic under concurrency as it is without
 * it. The one running total, expanded bytes, accumulates atomically, and a sum is commutative: the
 * total after the last charge is the same whatever order the charges arrived in, so whether a corpus
 * breaches that ceiling never depends on scheduling.
 *
 * <p>The documented relaxation is attribution, not outcome. When the total ceiling is breached, the
 * charge that crosses it belongs to whichever artifact happened to be reading at that moment, so the
 * entry this exception names may differ between two runs over the same inputs. A breach is an aborted
 * run — exit status and diagnostic, never a report — so no machine output depends on the answer.
 */
final class ResourceBudget {
    /** Largest number of entries a single archive or class directory may declare. */
    static final int MAXIMUM_ARCHIVE_ENTRIES = 262144;

    /** Largest number of bytes one class file may occupy. */
    static final int MAXIMUM_CLASS_FILE_BYTES = 33554432;

    /**
     * Largest number of bytes one entry of a nested layout may occupy: a library archive an application
     * carries inside itself, or the index that orders those libraries. Such an entry is read whole,
     * because bytes that never became a file cannot be addressed any other way, so it needs a ceiling of
     * its own rather than the one a single class file answers to.
     */
    static final int MAXIMUM_NESTED_ENTRY_BYTES = 268435456;

    /** Largest number of bytes one run may read out of all archives together. */
    static final long MAXIMUM_EXPANDED_BYTES = 4294967296L;

    /** Largest factor by which one stored entry may expand when read. */
    static final long MAXIMUM_COMPRESSION_RATIO = 200L;

    /** Largest number of findings one run may report. */
    static final int MAXIMUM_FINDINGS = 20000;

    /** How every ceiling on the size of a single entry names the entry that broke it. */
    private static final String EXCEEDED_BY = " bytes, exceeded by ";

    private final AtomicLong expandedBytes = new AtomicLong();

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
                    "A class file may occupy at most " + MAXIMUM_CLASS_FILE_BYTES + EXCEEDED_BY + entryName);
        }
    }

    /** Rejects a nested layout entry larger than the ceiling allows; a negative size is simply unknown. */
    void checkNestedEntryBytes(long bytes, String entryName) {
        if (bytes > MAXIMUM_NESTED_ENTRY_BYTES) {
            throw new IllegalStateException("A nested layout entry may occupy at most "
                    + MAXIMUM_NESTED_ENTRY_BYTES + EXCEEDED_BY + entryName);
        }
    }

    /** Adds bytes read out of an archive and rejects a run that expands more than the ceiling allows. */
    void addExpandedBytes(long bytes, String entryName) {
        if (expandedBytes.addAndGet(bytes) > MAXIMUM_EXPANDED_BYTES) {
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
