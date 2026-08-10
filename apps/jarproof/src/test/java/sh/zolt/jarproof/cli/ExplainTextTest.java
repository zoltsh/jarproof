package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.FindingCode;

final class ExplainTextTest {
    private static final List<String> DOCUMENTED_CODES = List.of(
            "JP1001", "JP1002", "JP1003", "JP1004", "JP1005", "JP1006", "JP1007",
            "JP2001", "JP2002", "JP2003", "JP2004", "JP2006", "JP2007", "JP2008",
            "JP3001", "JP3002", "JP3003", "JP3004", "JP3005",
            "JP4001", "JP4002", "JP4003", "JP4004", "JP4005",
            "JP5001", "JP5002", "JP5003", "JP5004", "JP5005", "JP5006");

    @Test
    void documentsEveryCodeThisReleaseImplements() {
        for (String code : DOCUMENTED_CODES) {
            Optional<String> text = ExplainText.of(FindingCode.of(code));
            assertTrue(text.isPresent(), code);
            assertTrue(text.get().startsWith(code + " "), code);
        }
    }

    @Test
    void keepsEveryTextWithinTheLengthAReaderTolerates() {
        for (String code : DOCUMENTED_CODES) {
            List<String> lines = ExplainText.of(FindingCode.of(code)).orElseThrow().lines().toList();
            assertTrue(lines.size() >= 10 && lines.size() <= 20, code + " has " + lines.size() + " lines");
            for (String line : lines) {
                assertTrue(line.length() <= 80, code + ": " + line);
            }
        }
    }

    @Test
    void explainsHowTheJvmGetsThereAndHowToFixIt() {
        String text = ExplainText.of(FindingCode.of("JP1003")).orElseThrow();

        assertTrue(text.contains("How the JVM gets there"), text);
        assertTrue(text.contains("Typical fix"), text);
        assertTrue(text.contains("NoSuchMethodError"), text);
        assertTrue(text.endsWith("\n"), text);
    }

    @Test
    void hasNoTextForAReservedCode() {
        assertEquals(Optional.empty(), ExplainText.of(FindingCode.of("JP1008")));
        assertEquals(Optional.empty(), ExplainText.of(FindingCode.of("JP2005")));
    }
}
