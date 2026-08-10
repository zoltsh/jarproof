package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class MainTest {
    @Test
    void printsFocusedHelp() {
        Invocation invocation = invoke("--help");

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.out().contains("Find JAR hell before production."));
        assertTrue(invocation.out().contains("Usage: jarproof"));
        assertEquals("", invocation.err());
    }

    @Test
    void showsHelpWhenNoCommandIsGiven() {
        Invocation invocation = invoke();

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.out().contains("Usage: jarproof"));
    }

    @Test
    void rejectsCommandsThatDoNotExistYet() {
        Invocation invocation = invoke("check");

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("Unmatched argument"));
    }

    private static Invocation invoke(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exitCode = Main.execute(new PrintWriter(out, true), new PrintWriter(err, true), args);
        return new Invocation(exitCode, out.toString(), err.toString());
    }

    private record Invocation(int exitCode, String out, String err) {
    }
}
