package sh.zolt.jarproof.cli;

/**
 * The flag names more than one command spells.
 *
 * <p>A flag that belongs to a single command is written where that command declares it. These two
 * are shared, so they live here instead: {@code check} and {@code inspect} both choose a format and
 * both can redirect their report, and the two spellings have to stay identical.
 */
final class FlagName {
    /** Chooses which format a report is rendered in. */
    static final String FORMAT = "--format";

    /** Redirects a report to a file instead of the process output stream. */
    static final String OUTPUT = "--output";

    private FlagName() {
    }
}
