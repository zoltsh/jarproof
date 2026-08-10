package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class MainTest {
    @Test
    void printsFocusedHelp() {
        Invocation invocation = CliFixture.invoke("--help");

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.out().contains("Find JAR hell before production."));
        assertTrue(invocation.out().contains("Usage: jarproof"));
        assertEquals("", invocation.err());
    }

    @Test
    void showsHelpWhenNoCommandIsGiven() {
        Invocation invocation = CliFixture.invoke();

        assertEquals(0, invocation.exitCode());
        assertTrue(invocation.out().contains("Usage: jarproof"));
    }

    @Test
    void offersEveryCommandItImplements() {
        String help = CliFixture.invoke("--help").out();

        assertTrue(help.contains("check"), help);
        assertTrue(help.contains("baseline"), help);
        assertTrue(help.contains("inspect"), help);
        assertTrue(help.contains("explain"), help);
    }

    @Test
    void printsTheProductVersionBanner() {
        Invocation invocation = CliFixture.invoke("--version");

        assertEquals(0, invocation.exitCode());
        assertEquals("jarproof 0.0.1-SNAPSHOT\n", invocation.out());
    }

    @Test
    void rejectsACommandThatDoesNotExist() {
        Invocation invocation = CliFixture.invoke("audit");

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("Unmatched argument"), invocation.err());
    }

    @Test
    void rejectsACheckWithoutTheFlagsItNeeds() {
        Invocation invocation = CliFixture.invoke(CliFixture.CHECK);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("--application"), invocation.err());
        assertTrue(invocation.err().contains("--target-java"), invocation.err());
    }
}
