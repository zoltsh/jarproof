package sh.zolt.jarproof.cli;

/**
 * The flag names more than one command spells.
 *
 * <p>A flag that belongs to a single command is written where that command declares it. These three
 * are shared, so they live here instead: {@code check} and {@code inspect} both choose a format,
 * both can redirect their document, and both measure the artifact paths their machine form reports,
 * and the spellings have to stay identical wherever they are declared.
 */
final class FlagName {
    /** Chooses which format a report is rendered in. */
    static final String FORMAT = "--format";

    /** Redirects a report to a file instead of the process output stream. */
    static final String OUTPUT = "--output";

    /** Names the root that machine output measures artifact paths from. */
    static final String PATH_ROOT = "--path-root";

    private FlagName() {
    }
}
