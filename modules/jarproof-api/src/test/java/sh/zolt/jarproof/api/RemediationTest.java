package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RemediationTest {
    @Test
    void keepsTheImperativeAction() {
        Remediation remediation = new Remediation("align the runtime classpath with the compile classpath");

        assertEquals("align the runtime classpath with the compile classpath", remediation.action());
    }

    @Test
    void rejectsAMissingAction() {
        assertThrows(NullPointerException.class, () -> new Remediation(null));
    }

    @Test
    void rejectsABlankAction() {
        assertThrows(IllegalArgumentException.class, () -> new Remediation(""));
        assertThrows(IllegalArgumentException.class, () -> new Remediation("\t\t"));
    }

    @Test
    void comparesByAction() {
        Remediation first = new Remediation("upgrade guava to 33.0.0-jre");
        Remediation same = new Remediation("upgrade guava to 33.0.0-jre");
        Remediation other = new Remediation("remove the duplicate artifact");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
    }
}
