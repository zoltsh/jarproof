package sh.zolt.jarproof.cli;

/**
 * The process statuses jarproof promises, as a closed vocabulary.
 *
 * <p>These three values are a documented contract: a release gate scripts against them, so their
 * numbers never change and no command invents a fourth.
 */
enum ExitCode {
    /** Nothing at or above the configured threshold was found. */
    CLEAN(0),

    /** Findings reached the configured {@code --fail-on} threshold. */
    FINDINGS(1),

    /** The invocation was wrong, or jarproof itself failed. */
    INVOCATION(2);

    private final int status;

    ExitCode(int status) {
        this.status = status;
    }

    /** Returns the process status this outcome reports. */
    int status() {
        return status;
    }
}
