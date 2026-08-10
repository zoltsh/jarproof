package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.VerificationRequest;

final class ResourceBudgetTest {
    private static final String ARTIFACT = "lib/huge.jar";
    private static final String ENTRY = "com/acme/huge/Huge.class";
    private static final int CHARGING_THREADS = 8;
    private static final int CHARGES_EACH = 1024;
    private static final int CHARGING_SECONDS = 30;
    private static final int CHARGED_ARTIFACTS = 8;

    @TempDir
    Path workspace;

    @Test
    void refusesAnArtifactWithTooManyEntries() {
        ResourceBudget budget = new ResourceBudget();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> budget.countArchiveEntries(ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES + 1, ARTIFACT));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES)),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(ARTIFACT), failure.getMessage());
    }

    @Test
    void acceptsAnArtifactAtTheEntryCeiling() {
        ResourceBudget budget = new ResourceBudget();

        assertDoesNotThrow(() -> budget.countArchiveEntries(ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES, ARTIFACT));
    }

    @Test
    void refusesAClassFileLargerThanTheCeiling() {
        ResourceBudget budget = new ResourceBudget();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> budget.checkClassFileBytes(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1L, ENTRY));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES)),
                failure.getMessage());
    }

    @Test
    void treatsAnUnknownEntrySizeAsWithinTheCeiling() {
        ResourceBudget budget = new ResourceBudget();

        assertDoesNotThrow(() -> budget.checkClassFileBytes(-1L, ENTRY));
    }

    @Test
    void refusesARunThatExpandsMoreBytesThanTheCeiling() {
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES, ENTRY);

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> budget.addExpandedBytes(1L, ENTRY));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_EXPANDED_BYTES)),
                failure.getMessage());
    }

    @Test
    void refusesAnEntryThatExpandsBeyondTheRatioCeiling() {
        ResourceBudget budget = new ResourceBudget();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> budget.checkCompressionRatio(
                1L, ResourceBudget.MAXIMUM_COMPRESSION_RATIO + 1L, ENTRY));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_COMPRESSION_RATIO)),
                failure.getMessage());
    }

    @Test
    void skipsTheRatioWhenEitherSizeIsUnknownOrEmpty() {
        ResourceBudget budget = new ResourceBudget();

        assertDoesNotThrow(() -> budget.checkCompressionRatio(0L, 1000L, ENTRY));
        assertDoesNotThrow(() -> budget.checkCompressionRatio(1000L, 0L, ENTRY));
        assertDoesNotThrow(() -> budget.checkCompressionRatio(
                1L, ResourceBudget.MAXIMUM_COMPRESSION_RATIO, ENTRY));
    }

    @Test
    void refusesARunThatReportsMoreFindingsThanTheCeiling() {
        ResourceBudget budget = new ResourceBudget();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> budget.checkFindingCount(ResourceBudget.MAXIMUM_FINDINGS + 1));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_FINDINGS)),
                failure.getMessage());
        assertDoesNotThrow(() -> budget.checkFindingCount(ResourceBudget.MAXIMUM_FINDINGS));
    }

    @Test
    void refusesAnArchiveEntryThatCompressesTooWellToBeReal() {
        Path archive = EngineFixture.jar(workspace, ARTIFACT, EngineFixture.entries(ENTRY, new byte[300_000]));
        VerificationRequest request = EngineFixture.request(List.of(archive), List.of(), 17);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> Jarproof.verify(request));

        assertTrue(failure.getMessage().contains(ENTRY), failure.getMessage());
    }

    /**
     * Charges the running total from several threads at once, in shares that add up to exactly the
     * ceiling, and then asks for one byte more. A lost update would leave the total short and that byte
     * would be accepted, so this fails on any accumulation that is not atomic — which is what a parallel
     * scan needs of it.
     */
    @Test
    void addsUpEveryChargeWhenSeveralArtifactsAreChargedAtOnce() throws InterruptedException {
        ResourceBudget budget = new ResourceBudget();
        long share = ResourceBudget.MAXIMUM_EXPANDED_BYTES / (CHARGING_THREADS * CHARGES_EACH);
        ExecutorService charging = Executors.newFixedThreadPool(CHARGING_THREADS);
        try {
            for (int thread = 0; thread < CHARGING_THREADS; thread++) {
                charging.execute(() -> charge(budget, share));
            }
            charging.shutdown();
            assertTrue(charging.awaitTermination(CHARGING_SECONDS, TimeUnit.SECONDS));
        } finally {
            charging.shutdownNow();
        }

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> budget.addExpandedBytes(1L, ENTRY));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_EXPANDED_BYTES)),
                failure.getMessage());
    }

    /**
     * A corpus already at the ceiling still breaches under a parallel scan. Which position is charged
     * for crossing it is the documented relaxation, so the entry name is deliberately not asserted; that
     * the run refuses, naming the ceiling it refused for, is not relaxed at all.
     */
    @Test
    void refusesACorpusOverTheExpandedByteCeilingWhicheverArtifactCrossesIt() {
        List<Path> libraries = new ArrayList<>();
        for (int index = 0; index < CHARGED_ARTIFACTS; index++) {
            String internalName = "com/acme/charged/Charged" + index;
            libraries.add(EngineFixture.jar(workspace, "charged" + index + ".jar",
                    EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName))));
        }
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES, ENTRY);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ArtifactCatalog.read(
                EngineFixture.request(
                        List.of(libraries.get(0)), libraries.subList(1, CHARGED_ARTIFACTS), 17),
                budget));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_EXPANDED_BYTES)),
                failure.getMessage());
    }

    private static void charge(ResourceBudget budget, long share) {
        for (int charged = 0; charged < CHARGES_EACH; charged++) {
            budget.addExpandedBytes(share, ENTRY);
        }
    }
}
