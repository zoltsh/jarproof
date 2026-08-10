package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FindingCodeTest {
    @Test
    void preservesCanonicalValue() {
        FindingCode code = FindingCode.of("JP1003");

        assertEquals("JP1003", code.value());
        assertEquals("JP1003", code.toString());
    }

    @Test
    void comparesAndHashesByValue() {
        FindingCode first = FindingCode.of("JP1001");
        FindingCode same = FindingCode.of("JP1001");
        FindingCode later = FindingCode.of("JP2001");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, later);
        assertTrue(first.compareTo(later) < 0);
    }

    @Test
    void rejectsNonCanonicalCodes() {
        assertThrows(NullPointerException.class, () -> FindingCode.of(null));
        assertThrows(IllegalArgumentException.class, () -> FindingCode.of("JP003"));
        assertThrows(IllegalArgumentException.class, () -> FindingCode.of("jp1003"));
        assertThrows(IllegalArgumentException.class, () -> FindingCode.of(" JP1003"));
    }

    @Test
    void rejectsNullComparison() {
        FindingCode code = FindingCode.of("JP1001");

        assertThrows(NullPointerException.class, () -> code.compareTo(null));
    }
}
