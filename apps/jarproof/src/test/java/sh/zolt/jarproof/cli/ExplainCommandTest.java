package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
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
    void listsEveryDocumentedCodeWhenAskedForNoneOfThem() {
        Invocation invocation = invoke("explain");

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("", invocation.err());
        assertEquals(
                """
                JP1001  missing class
                JP1002  missing field
                JP1003  missing method
                JP1004  static and instance mismatch
                JP1005  class and interface kind mismatch
                JP1006  inaccessible class
                JP1007  inaccessible member
                JP2001  duplicate class with identical bytecode
                JP2002  duplicate class with differing bytecode
                JP2003  split package across artifacts
                JP2004  same artifact present in multiple versions
                JP2006  duplicate winner depends on wildcard expansion order
                JP2007  manifest Class-Path entry not found
                JP2008  sealed package split across artifacts
                JP3001  class file newer than the target release
                JP3002  preview class file used incorrectly
                JP3003  invalid multi-release JAR layout
                JP3004  unparseable class file
                JP3005  mixed bytecode levels in one artifact
                JP3006  invalid nested application archive layout
                JP4001  service provider class missing
                JP4002  provider does not implement the service type
                JP4003  malformed META-INF/services file
                JP4004  duplicate service provider entry
                JP4005  provider not instantiable
                JP5001  required module resolves nowhere
                JP5002  exposed package holds no classes here
                JP5003  module service provider unusable
                JP5004  consumed service type resolves nowhere
                JP5005  package split across module-capable artifacts
                JP5006  reserved automatic module name is not a legal module name
                """,
                invocation.out());
    }

    /**
     * The listing names a code exactly once and never the reserved ones, so a reader can take it as the
     * complete set rather than a sample of it.
     */
    @Test
    void listsEachCodeOnceAndNoReservedCode() {
        List<String> listed = invoke("explain").out().lines()
                .map(line -> line.substring(0, line.indexOf(' ')))
                .toList();

        assertEquals(listed.stream().distinct().sorted().toList(), listed);
        assertFalse(listed.contains("JP1008"), listed.toString());
        assertFalse(listed.contains("JP2005"), listed.toString());
    }

    @Test
    void refusesMoreThanOneCode() {
        Invocation invocation = invoke("explain", "JP1003", "JP1004");

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
        assertTrue(invocation.err().contains("JP1004"), invocation.err());
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
