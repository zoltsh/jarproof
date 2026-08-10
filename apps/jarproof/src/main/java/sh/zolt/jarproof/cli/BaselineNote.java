package sh.zolt.jarproof.cli;

/**
 * What a baseline says about itself on the diagnostic stream.
 *
 * <p>Three notes, one per thing a baseline does: what recording it wrote, what applying it
 * suppressed and how much of it has gone stale, and what pruning it removed. They live together
 * because they are read together -- a team ratcheting a baseline down reads the stale count of one
 * run and the pruned count of the next -- and because they count the same two nouns, so the
 * grammar has to match across all three.
 */
final class BaselineNote {
    private static final String ACCEPTED_FINDING = "accepted finding";
    private static final String BASELINE_ENTRY = "baseline entry";
    private static final String BASELINE_ENTRIES = "baseline entries";
    private static final String STALE = "stale ";
    private static final String SUPPRESSED = "suppressed ";
    private static final String THEN = "; ";
    private static final String IS_STALE = " is stale";
    private static final String ARE_STALE = " are stale";
    private static final String PRUNED = "pruned ";

    private BaselineNote() {
    }

    /**
     * Notes what applying a baseline to a fresh run did.
     *
     * @param suppressed how many of the run's findings the baseline already accepted
     * @param stale how many accepted entries the run no longer observed
     * @return the one line the diagnostic stream carries
     */
    static String applied(int suppressed, int stale) {
        return SUPPRESSED + accepted(suppressed) + THEN
                + Quantity.of(stale, BASELINE_ENTRY, BASELINE_ENTRIES)
                + (stale == 1 ? IS_STALE : ARE_STALE);
    }

    /**
     * Notes what pruning a baseline removed.
     *
     * @param stale how many accepted entries were dropped
     * @return the one line the diagnostic stream carries
     */
    static String pruned(int stale) {
        return PRUNED + Quantity.of(stale, STALE + BASELINE_ENTRY, STALE + BASELINE_ENTRIES);
    }

    /**
     * Names a number of accepted findings, which is what a recording wrote.
     *
     * @param findings how many findings were accepted
     * @return the counted noun, without any surrounding sentence
     */
    static String accepted(int findings) {
        return Quantity.of(findings, ACCEPTED_FINDING);
    }
}
