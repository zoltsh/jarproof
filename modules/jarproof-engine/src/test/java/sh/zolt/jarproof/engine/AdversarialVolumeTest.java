package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * Crafted volume: archives that ask a reader to spend more than the run is allowed to spend.
 *
 * <p>These are the inputs that cost nothing to send and everything to read -- an entry count no build
 * produces, an entry that claims to expand a thousandfold, a manifest measured in megabytes. Each one is
 * met with either a ceiling that names itself or an ordinary answer, and both are asserted from the two
 * commands that read artifacts, because a ceiling only bounds the reader that consults it.
 *
 * <p>What is deliberately absent is a case that succeeds slowly. A ceiling exists so a refusal arrives
 * before the work does, so each of these refusals is asserted to name the limit and the input, which is
 * what turns an aborted run into a diagnosis.
 */
final class AdversarialVolumeTest {
    private static final String ALPHA = "com/acme/adversary/Alpha";
    private static final String ALPHA_ENTRY = ALPHA + ArchiveLayout.CLASS_SUFFIX;
    private static final int JAVA_17_LEVEL = 17;
    private static final int RATIO_BREACHING_BYTES = 300_000;
    private static final int MANIFEST_ATTRIBUTES = 200_000;
    private static final int MEGABYTE = 1_048_576;

    @TempDir
    Path workspace;

    /**
     * More entries than the ceiling allows, each of them empty, which is what makes the shape worth
     * refusing: the archive costs almost nothing to build and would cost a reader an entry's work
     * hundreds of thousands of times over. Both readers count before they read.
     */
    @Test
    void refusesAnArchiveThatDeclaresMoreEntriesThanTheCeiling() {
        Path artifact = crowdedArchive();

        assertNamesTheCeiling(
                assertThrows(IllegalStateException.class, () -> codes(artifact)),
                ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES,
                artifact.toString());
        assertNamesTheCeiling(
                assertThrows(IllegalStateException.class, () -> Jarproof.inspect(artifact)),
                ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES,
                artifact.toString());
    }

    /**
     * An entry whose declared size dwarfs the bytes actually stored for it. The ratio is read from what
     * the headers claim, before a byte is expanded, so the refusal costs the run nothing at all -- and it
     * names the entry rather than the artifact, because the entry is what a packager has to look at.
     */
    @Test
    void refusesAnEntryThatDeclaresFarMoreBytesThanItStores() {
        Path artifact = write("balloon.jar", AdversarialArchives.archive(
                EngineFixture.entries(ALPHA_ENTRY, new byte[RATIO_BREACHING_BYTES]), Set.of()));

        assertNamesTheCeiling(
                assertThrows(IllegalStateException.class, () -> codes(artifact)),
                ResourceBudget.MAXIMUM_COMPRESSION_RATIO,
                ALPHA_ENTRY);
        assertNamesTheCeiling(
                assertThrows(IllegalStateException.class, () -> Jarproof.inspect(artifact)),
                ResourceBudget.MAXIMUM_COMPRESSION_RATIO,
                ALPHA_ENTRY);
    }

    /**
     * A manifest of several megabytes, which is legal and pointless in equal measure. Measured, then
     * pinned: it is parsed as the attributes it is and the run completes with the findings the archive
     * deserves, so the answer is an ordinary one.
     *
     * <p>What this deliberately does not claim is a ceiling. A top-level archive's manifest is parsed
     * where the classpath is assembled, outside the run's expanded-byte ledger, so nothing here bounds
     * its size: the honest statement is that megabytes are survivable, not that gigabytes would be
     * refused. A manifest inside a nested library is a different matter, because that one is read through
     * the budget like every other entry of it.
     */
    @Test
    void readsAnArchiveWhoseManifestRunsToSeveralMegabytes() {
        byte[] manifest = paddedManifest();
        Map<String, byte[]> entries = EngineFixture.entries(JarFile.MANIFEST_NAME, manifest);
        entries.put(ALPHA_ENTRY, EngineFixture.classFile(ALPHA));
        Path artifact = write("verbose.jar", AdversarialArchives.archive(entries, Set.of()));

        assertTrue(manifest.length > MEGABYTE, () -> manifest.length + " bytes is not several megabytes");
        assertEquals(List.of(), codes(artifact));
        ArtifactSummary summary = Jarproof.inspect(artifact);
        assertEquals(2, summary.entryCount());
        assertEquals(1, summary.classCount());
        assertEquals(List.of(EngineFixture.JAVA_17_MAJOR + ":1"), summary.bytecodeLevels());
    }

    private static void assertNamesTheCeiling(IllegalStateException refused, long ceiling, String input) {
        assertTrue(refused.getMessage().contains(String.valueOf(ceiling)), refused::getMessage);
        assertTrue(refused.getMessage().contains(input), refused::getMessage);
    }

    private Path crowdedArchive() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int index = 0; index <= ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES; index++) {
            entries.put("e/" + index, new byte[0]);
        }
        return write("crowded.jar", AdversarialArchives.archive(entries, Set.of()));
    }

    /** A well-formed manifest whose main section holds attributes nobody reads, by the tens of thousands. */
    private static byte[] paddedManifest() {
        StringBuilder manifest = new StringBuilder("Manifest-Version: 1.0\r\n");
        for (int attribute = 0; attribute < MANIFEST_ATTRIBUTES; attribute++) {
            manifest.append("X-Padding-").append(attribute).append(": ignored\r\n");
        }
        return manifest.append("\r\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    private Path write(String name, byte[] content) {
        return AdversarialArchives.write(workspace, name, content);
    }

    private static List<String> codes(Path artifact) {
        return EngineFixture.codes(EngineFixture.verify(List.of(artifact), List.of(), JAVA_17_LEVEL));
    }
}
