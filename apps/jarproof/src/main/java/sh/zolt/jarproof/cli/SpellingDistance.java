package sh.zolt.jarproof.cli;

/**
 * How far apart two spellings are, counted in single-character edits.
 *
 * <p>This is the Levenshtein distance: the fewest insertions, deletions, and substitutions that turn
 * one text into the other. It exists so a refused flag can name the flag it probably meant, which the
 * parser's own matcher answers only for a truncation -- it compares by leading characters, so
 * {@code --applicaton} finds {@code --application} and {@code --fromat} finds nothing at all.
 *
 * <p>Two adjacent characters written the wrong way round cost two edits here rather than the one a
 * transposition-aware measure would charge, and that is exactly why the ceiling this is compared
 * against is two: a single transposition stays inside it, while two unrelated flags do not come close.
 * Paying for the third row a transposition rule needs would buy a better-ordered list of candidates
 * and no additional candidate.
 *
 * <p>Only two rows of the edit table are kept, because the distance of one row depends on nothing
 * further back than the row before it.
 */
final class SpellingDistance {
    private SpellingDistance() {
    }

    /**
     * Counts the edits between two spellings.
     *
     * @param written the text as it was written
     * @param candidate the text it might have meant
     * @return the number of single-character edits between them
     */
    static int between(String written, String candidate) {
        int width = candidate.length();
        int[] previous = new int[width + 1];
        int[] current = new int[width + 1];
        for (int column = 0; column <= width; column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= written.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= width; column++) {
                int substitution = written.charAt(row - 1) == candidate.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitution);
            }
            int[] finished = previous;
            previous = current;
            current = finished;
        }
        return previous[width];
    }
}
