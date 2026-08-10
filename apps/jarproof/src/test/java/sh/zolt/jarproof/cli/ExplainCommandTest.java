package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class ExplainCommandTest {
    @Test
    void printsTheDocumentationForAKnownCode() {
        Invocation invocation = invoke("explain", "JP1003");

        assertEquals(0, invocation.exitCode());
        assertEquals("", invocation.err());
        assertEquals(
                """
                JP1003 missing method

                A class file calls a method that the resolved owner does not declare with that
                exact name and descriptor, and no superclass or superinterface supplies it.
                """,
                invocation.out().lines().limit(4).reduce("", (all, line) -> all + line + '\n'));
        assertTrue(invocation.out().contains("Predicted runtime error: NoSuchMethodError"), invocation.out());
    }

    @Test
    void printsTheWholeTextExactlyAsWritten() {
        Invocation invocation = invoke("explain", "JP2006");

        assertEquals(
                """
                JP2006 duplicate winner depends on wildcard expansion order

                A class is declared by more than one artifact with differing content, and the
                competing copies arrive through the same classpath wildcard, so no rule
                determines which one the JVM will choose.
                """,
                invocation.out().lines().limit(5).reduce("", (all, line) -> all + line + '\n'));
        assertTrue(invocation.out().endsWith("also resolves this finding.\n"), invocation.out());
    }

    @Test
    void failsWithTheInvocationStatusForAReservedCode() {
        Invocation invocation = invoke("explain", "JP1008");

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
        assertEquals(
                """
                No explanation is available for JP1008
                Codes stay reserved until their check ships; DESIGN.md lists the ranges.
                """,
                invocation.err());
    }

    @Test
    void failsWithTheInvocationStatusForTextThatIsNotACode() {
        Invocation invocation = invoke("explain", "jp1003");

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
        assertEquals(
                """
                Not a jarproof diagnostic code: jp1003
                A code is JP followed by four digits, such as JP1003.
                """,
                invocation.err());
    }

    @Test
    void requiresACode() {
        Invocation invocation = invoke("explain");

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("Missing required parameter: 'CODE'"), invocation.err());
    }

    @Test
    void appearsInTheRootUsage() {
        Invocation invocation = invoke("--help");

        assertTrue(invocation.out().contains("explain"), invocation.out());
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
