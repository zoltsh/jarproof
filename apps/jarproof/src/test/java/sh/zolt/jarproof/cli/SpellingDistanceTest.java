package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The edit distance itself, measured directly rather than through the suggestion line it feeds.
 *
 * <p>{@link UsageError} only ever asks whether a distance is within two, so every property this
 * measure promises survives inside that comparison: a distance that came back far too small still
 * offers the same flag, and the tests that read the suggestion line cannot tell. The claims the
 * class documents -- a distance from nothing is a length, a transposition costs two edits, and two
 * unrelated flags are nowhere near each other -- are therefore asserted here on the numbers.
 */
final class SpellingDistanceTest {
    private static final String FORMAT = "--format";

    /**
     * Turning nothing into a text costs one insertion per character, in both directions. This is the
     * edit table's first row and column, and it is the only case where nothing else fills them in.
     */
    @Test
    void countsTheWholeTextWhenOneSideIsEmpty() {
        assertEquals(FORMAT.length(), SpellingDistance.between("", FORMAT));
        assertEquals(FORMAT.length(), SpellingDistance.between(FORMAT, ""));
        assertEquals(0, SpellingDistance.between("", ""));
    }

    @Test
    void countsNoEditsBetweenIdenticalSpellings() {
        assertEquals(0, SpellingDistance.between(FORMAT, FORMAT));
    }

    @Test
    void countsOneEditPerSingleCharacterMistake() {
        assertEquals(1, SpellingDistance.between("--frmat", FORMAT));
        assertEquals(1, SpellingDistance.between("--formats", FORMAT));
        assertEquals(1, SpellingDistance.between("--formax", FORMAT));
    }

    /**
     * The measure charges two edits for a transposition rather than the one a transposition-aware
     * measure would, which is exactly why the ceiling it is compared against is two: the commonest
     * typo there is stays inside it.
     */
    @Test
    void chargesTwoEditsForTwoCharactersTheWrongWayRound() {
        assertEquals(2, SpellingDistance.between("--fromat", FORMAT));
        assertEquals(2, SpellingDistance.between("ab", "ba"));
    }

    /** Two unrelated flags are not near each other, which is what stops a wrong guess being offered. */
    @Test
    void keepsUnrelatedSpellingsWellApart() {
        assertTrue(SpellingDistance.between("--bogus", FORMAT) > 2, FORMAT);
        assertTrue(SpellingDistance.between("--application", FORMAT) > 2, FORMAT);
    }
}
