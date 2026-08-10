package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class BaselineRoundTripTest {
    private static final String BASELINE = "baseline";
    private static final String BASELINE_FLAG = "--baseline";
    private static final String OUT = "--out";
    private static final String SECOND_VALIDATOR = "com/acme/app/PriceValidator";
    private static final String NOTHING_NEW = "no findings\n";
    private static final String ACCEPTED_ONE = "suppressed 1 accepted finding; 0 baseline entries are stale\n";

    @TempDir
    Path workspace;

    @Test
    void recordsEveryFindingItFoundAndKeepsTheOutputStreamClean() throws IOException {
        Invocation invocation = record();

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("", invocation.out());
        assertEquals(
                """
                {
                  "baselineVersion": "1",
                  "targetJava": 17,
                  "preview": "disabled",
                  "scope": "application",
                  "profile": "bundled:17",
                  "fingerprints": [
                    "JP1003|app.jar|com/acme/app/OrderValidator.class|com/acme/api/OrderPolicy#check(Ljava/lang/String;)V"
                  ]
                }
                """,
                CliFixture.rooted(Files.readString(baselineFile()), workspace));
    }

    @Test
    void acceptsEverythingItJustRecorded() throws IOException {
        record();

        Invocation invocation = check(BASELINE_FLAG, baselineFile().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(NOTHING_NEW, invocation.out());
        assertEquals(ACCEPTED_ONE, invocation.err());
    }

    @Test
    void reportsOnlyTheFindingThatIsNew() throws IOException {
        record();

        Invocation invocation = CliFixture.invoke(CliFixture.checkArgs(
                twoBreakages(), CliFixture.library(workspace), BASELINE_FLAG, baselineFile().toString()));

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains(SECOND_VALIDATOR + ".class"), invocation.out());
        assertFalse(invocation.out().contains(CliFixture.VALIDATOR + ".class"), invocation.out());
        assertTrue(invocation.out().endsWith("1 error, 1 finding\n"), invocation.out());
        assertEquals(ACCEPTED_ONE, invocation.err());
    }

    @Test
    void countsAnAcceptedFindingThatNoLongerOccursAsStale() throws IOException {
        recordFrom(twoBreakages());

        Invocation invocation = check(BASELINE_FLAG, baselineFile().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals("suppressed 1 accepted finding; 1 baseline entry is stale\n", invocation.err());
    }

    /**
     * Two spellings of one path describe one finding. A fingerprint that kept the caller's own text
     * would call the accepted finding new and the recorded one stale, which is the exact failure a
     * baseline exists to prevent -- and the spelling difference can be as small as a leading
     * {@code ./} that a shell or a build tool added.
     */
    @Test
    void acceptsTheSameFindingThroughADifferentSpellingOfTheSamePath() throws IOException {
        record();

        Invocation invocation = CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                dotted(CliFixture.brokenApplication(workspace)),
                CliFixture.CLASSPATH,
                dotted(CliFixture.library(workspace)),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                BASELINE_FLAG,
                baselineFile().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(NOTHING_NEW, invocation.out());
        assertEquals(ACCEPTED_ONE, invocation.err());
    }

    @Test
    void warnsWhenTheBaselineWasRecordedAgainstSomethingElse() throws IOException {
        record();
        Files.writeString(baselineFile(), Files.readString(baselineFile())
                .replace("\"targetJava\": 17", "\"targetJava\": 11")
                .replace("\"scope\": \"application\"", "\"scope\": \"all\"")
                .replace("\"profile\": \"bundled:17\"", "\"profile\": \"jdk:11:0badcafe\""));

        Invocation invocation = check(BASELINE_FLAG, baselineFile().toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertEquals(NOTHING_NEW, invocation.out());
        assertTrue(invocation.err().contains("records targetJava 11, and this run measured 17"), invocation.err());
        assertTrue(invocation.err().contains("records scope all, and this run measured application"), invocation.err());
        assertTrue(
                invocation.err().contains("records profile jdk:11:0badcafe, and this run measured bundled:17"),
                invocation.err());
        assertTrue(invocation.err().contains("applying it anyway"), invocation.err());
    }

    @Test
    void refusesABaselineThatIsNotThere() {
        Path absent = workspace.resolve("absent.json");

        Invocation invocation = check(BASELINE_FLAG, absent.toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains(absent.toString()), invocation.err());
    }

    @Test
    void refusesABaselineThisBuildDoesNotUnderstand() throws IOException {
        Path stale = Files.writeString(workspace.resolve("old.json"), "{\"baselineVersion\": \"0\"}");

        Invocation invocation = check(BASELINE_FLAG, stale.toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("Unsupported baseline version: 0"), invocation.err());
    }

    @Test
    void refusesToRecordAnApplicationThatIsNotThere() {
        Path absent = workspace.resolve("absent.jar");

        Invocation invocation = CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                absent.toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                OUT,
                baselineFile().toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains(absent.toString()), invocation.err());
    }

    @Test
    void refusesToRecordWhereItCannotWrite() {
        Invocation invocation = CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                OUT,
                workspace.resolve("absent").resolve("baseline.json").toString());

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("absent"), invocation.err());
    }

    @Test
    void refusesToRecordWithoutAFileToRecordInto() {
        Invocation invocation = CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains(OUT), invocation.err());
    }

    private Invocation record() {
        return recordFrom(CliFixture.brokenApplication(workspace));
    }

    private Invocation recordFrom(Path application) {
        return CliFixture.invoke(
                BASELINE,
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                OUT,
                baselineFile().toString());
    }

    private Invocation check(String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(
                CliFixture.brokenApplication(workspace), CliFixture.library(workspace), extra));
    }

    private Path baselineFile() {
        return workspace.resolve("jarproof-baseline.json");
    }

    /** The same artifact, spelled the way a shell or a build tool that inserts {@code ./} would. */
    private static String dotted(Path artifact) {
        return artifact.getParent() + "/./" + artifact.getFileName();
    }

    /** The broken application plus a second class that breaks on the same missing method. */
    private Path twoBreakages() {
        Map<String, byte[]> entries = CliFixture.entries(
                CliFixture.VALIDATOR + CliFixture.CLASS_SUFFIX,
                CliFixture.breakingCaller(CliFixture.VALIDATOR));
        entries.put(
                SECOND_VALIDATOR + CliFixture.CLASS_SUFFIX, CliFixture.breakingCaller(SECOND_VALIDATOR));
        return CliFixture.jar(workspace, "app.jar", entries);
    }
}
