package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

final class BaselinePruneTest {
    private static final String BASELINE_FLAG = "--baseline";
    private static final String PRUNE_STALE = "--prune-stale";
    private static final String OUT = "--out";
    private static final String PRICE_VALIDATOR = "com/acme/app/PriceValidator";
    private static final String SHIPPING_VALIDATOR = "com/acme/app/ShippingValidator";
    private static final String PRUNED_ONE = "pruned 1 stale baseline entries\n";
    private static final String PRUNED_NONE = "pruned 0 stale baseline entries\n";

    @TempDir
    Path workspace;

    @Test
    void dropsTheStaleEntryAndKeepsEverythingTheFileRecordedAboutTheAcceptance() throws IOException {
        record(breaking(CliFixture.VALIDATOR, PRICE_VALIDATOR));
        recordedAgainstSomethingElse();

        Invocation invocation = prune(breaking(CliFixture.VALIDATOR));

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.err().endsWith(PRUNED_ONE), invocation.err());
        assertEquals(
                """
                {
                  "baselineVersion": "1",
                  "targetJava": 11,
                  "preview": "disabled",
                  "scope": "all",
                  "profile": "jdk:11:0badcafe",
                  "fingerprints": [
                    "JP1003|app.jar|com/acme/app/OrderValidator.class|com/acme/api/OrderPolicy#check(Ljava/lang/String;)V"
                  ]
                }
                """,
                CliFixture.rooted(Files.readString(baselineFile()), workspace));
    }

    /**
     * Nothing stale means nothing written, which is the promise {@code --prune-stale} documents: a
     * build watching the file sees a change only when the baseline really moved. Comparing the bytes
     * proves the content, and comparing the modification time proves the file was left alone rather
     * than rewritten with the same content.
     */
    @Test
    void writesNothingAtAllWhenNoAcceptedEntryHasGoneStale() throws IOException {
        Path application = breaking(CliFixture.VALIDATOR);
        record(application);
        byte[] recorded = Files.readAllBytes(baselineFile());
        FileTime written = Files.getLastModifiedTime(baselineFile());

        Invocation invocation = prune(application);

        assertEquals(0, invocation.exitCode(), invocation.err());
        assertTrue(invocation.err().endsWith(PRUNED_NONE), invocation.err());
        assertArrayEquals(recorded, Files.readAllBytes(baselineFile()), Files.readString(baselineFile()));
        assertEquals(written, Files.getLastModifiedTime(baselineFile()));
    }

    @Test
    void refusesToPruneWithoutABaselineToPrune() {
        Invocation invocation = check(breaking(CliFixture.VALIDATOR), PRUNE_STALE);

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
        assertEquals(
                PRUNE_STALE + " has nothing to prune without " + BASELINE_FLAG + "\n", invocation.err());
    }

    @Test
    void refusesABaselineFileItCannotRewrite() throws IOException {
        record(breaking(CliFixture.VALIDATOR, PRICE_VALIDATOR));
        Files.setPosixFilePermissions(baselineFile(), Set.of(PosixFilePermission.OWNER_READ));

        Invocation invocation = prune(breaking(CliFixture.VALIDATOR));

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.out());
        assertTrue(invocation.err().contains(baselineFile().toString()), invocation.err());
    }

    /**
     * Pruning is a side effect on the file, never on the report: the run that rewrites the baseline
     * has to render the same bytes and return the same status as the run that leaves it alone. The
     * unpruned run goes first because it is the one that cannot change what the second one reads.
     */
    @Test
    void reportsExactlyWhatTheSameRunWithoutPruningReports() throws IOException {
        record(breaking(CliFixture.VALIDATOR, PRICE_VALIDATOR));
        Path application = breaking(CliFixture.VALIDATOR, SHIPPING_VALIDATOR);

        Invocation kept = check(application, BASELINE_FLAG, baselineFile().toString());
        Invocation pruned = prune(application);

        assertEquals(1, kept.exitCode(), kept.err());
        assertEquals(kept.exitCode(), pruned.exitCode(), pruned.err());
        assertArrayEquals(utf8(kept.out()), utf8(pruned.out()), pruned.out());
        assertTrue(pruned.out().contains(SHIPPING_VALIDATOR + CliFixture.CLASS_SUFFIX), pruned.out());
        assertTrue(pruned.err().endsWith(PRUNED_ONE), pruned.err());
    }

    @Test
    void findsNothingLeftToPruneOnASecondRun() throws IOException {
        record(breaking(CliFixture.VALIDATOR, PRICE_VALIDATOR));
        Path application = breaking(CliFixture.VALIDATOR);

        Invocation first = prune(application);
        byte[] ratcheted = Files.readAllBytes(baselineFile());
        Invocation second = prune(application);

        assertTrue(first.err().endsWith(PRUNED_ONE), first.err());
        assertEquals(0, second.exitCode(), second.err());
        assertTrue(second.err().endsWith(PRUNED_NONE), second.err());
        assertArrayEquals(ratcheted, Files.readAllBytes(baselineFile()), Files.readString(baselineFile()));
    }

    private Invocation prune(Path application) {
        return check(application, BASELINE_FLAG, baselineFile().toString(), PRUNE_STALE);
    }

    private Invocation check(Path application, String... extra) {
        return CliFixture.invoke(CliFixture.checkArgs(application, CliFixture.library(workspace), extra));
    }

    private Invocation record(Path application) {
        return CliFixture.invoke(
                "baseline",
                CliFixture.APPLICATION,
                application.toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                OUT,
                baselineFile().toString());
    }

    /** The application artifact, holding one class per named breakage and nothing else. */
    private Path breaking(String... callers) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String caller : callers) {
            entries.put(caller + CliFixture.CLASS_SUFFIX, CliFixture.breakingCaller(caller));
        }
        return CliFixture.jar(workspace, "app.jar", entries);
    }

    /**
     * Rewrites the envelope to an acceptance this run does not share, so a preserved envelope is
     * visibly preserved rather than coincidentally regenerated to the same values.
     */
    private void recordedAgainstSomethingElse() throws IOException {
        Files.writeString(baselineFile(), Files.readString(baselineFile())
                .replace("\"targetJava\": 17", "\"targetJava\": 11")
                .replace("\"scope\": \"application\"", "\"scope\": \"all\"")
                .replace("\"profile\": \"bundled:17\"", "\"profile\": \"jdk:11:0badcafe\""));
    }

    private Path baselineFile() {
        return workspace.resolve("jarproof-baseline.json");
    }

    private static byte[] utf8(String report) {
        return report.getBytes(StandardCharsets.UTF_8);
    }
}
