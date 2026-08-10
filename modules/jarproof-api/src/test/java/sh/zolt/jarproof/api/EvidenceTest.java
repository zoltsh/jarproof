package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class EvidenceTest {
    @Test
    void keepsTheObservedDetail() {
        Evidence evidence = new Evidence("selected guava-18.0.jar for the reference");

        assertEquals("selected guava-18.0.jar for the reference", evidence.detail());
    }

    @Test
    void rejectsAMissingDetail() {
        assertThrows(NullPointerException.class, () -> new Evidence(null));
    }

    @Test
    void rejectsABlankDetail() {
        assertThrows(IllegalArgumentException.class, () -> new Evidence(""));
        assertThrows(IllegalArgumentException.class, () -> new Evidence(" \n "));
    }

    @Test
    void comparesByDetail() {
        Evidence first = new Evidence("class file version 61");
        Evidence same = new Evidence("class file version 61");
        Evidence other = new Evidence("class file version 65");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
    }
}
