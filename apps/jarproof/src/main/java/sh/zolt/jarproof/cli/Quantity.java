package sh.zolt.jarproof.cli;

/**
 * A counted noun, spelled the way a reader expects to read it.
 *
 * <p>Every count jarproof prints ends up in a sentence a person reads, and "1 findings" reads as a
 * defect in the tool rather than as one finding. Counting and pluralising in one place is what keeps
 * the report, the baseline notes, and the write confirmations agreeing about that.
 *
 * <p>Zero takes the plural, which is what English does: "0 findings", never "0 finding". An irregular
 * noun states its own plural rather than gaining an {@code s}, because "baseline entrys" would be the
 * same defect from the other direction.
 */
final class Quantity {
    private Quantity() {
    }

    /**
     * Counts a noun whose plural is its singular plus an {@code s}.
     *
     * @param occurrences how many there are
     * @param noun the singular noun
     * @return the count and the noun, pluralised unless there is exactly one
     */
    static String of(long occurrences, String noun) {
        return of(occurrences, noun, noun + 's');
    }

    /**
     * Counts a noun that spells its own plural.
     *
     * @param occurrences how many there are
     * @param singular the noun as one of them is named
     * @param plural the noun as several of them are named
     * @return the count and the matching form
     */
    static String of(long occurrences, String singular, String plural) {
        return occurrences + " " + (occurrences == 1 ? singular : plural);
    }
}
