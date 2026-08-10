package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.VerificationRequest;

final class ResourceBudgetTest {
    private static final String ARTIFACT = "lib/huge.jar";
    private static final String ENTRY = "com/acme/huge/Huge.class";

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
}
