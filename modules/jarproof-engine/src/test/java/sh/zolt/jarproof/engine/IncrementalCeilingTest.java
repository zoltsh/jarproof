package sh.zolt.jarproof.engine;

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

/**
 * The entry ceiling bounds the work it exists to bound, not only the answer.
 *
 * <p>An archive declaring a quarter of a million entries is met in two places: the streaming walk of a
 * library an application carries inside itself, and the entry names that application is expanded into.
 * Both count as they go and refuse at the entry that crosses the line. A refusal that arrived once every
 * entry had been read would name the same ceiling and would already have cost the run everything the
 * sender wanted it to cost, so what these assert is that the entries behind the ceiling are never read at
 * all: the archive ends with a manifest no parser accepts, and the ceiling is what answers.
 */
final class IncrementalCeilingTest {
    private static final String LIBRARY_ENTRY = BootLayoutFixture.LIBRARY_DIRECTORY + "crowded.jar";
    private static final String APPLICATION_CLASS = "com/acme/app/App";
    private static final String CROWD_PREFIX = "e/";
    private static final String UNPARSEABLE_MANIFEST = "this line carries no colon\r\n\r\n";

    private static byte[] crowded;

    @TempDir
    Path workspace;

    /**
     * A library whose entries outnumber the ceiling is refused at the entry that crosses it, so the
     * manifest it declares last is never parsed. Reading that manifest is the only other thing this walk
     * would have done, which is what makes it the evidence: the run stops where the ceiling is, not where
     * the archive ends.
     */
    @Test
    void refusesACrowdedNestedLibraryAtTheEntryThatCrossesTheCeiling() {
        Path application = BootLayoutFixture.archive()
                .withStoredLibrary(LIBRARY_ENTRY, crowdedArchive())
                .with(BootLayoutFixture.CLASSES_ROOT + APPLICATION_CLASS + ArchiveLayout.CLASS_SUFFIX,
                        EngineFixture.classFile(APPLICATION_CLASS))
                .write(workspace, "crowded-library.jar");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> EngineFixture.verify(List.of(application), List.of(), 17));

        assertNamesTheEntryCeiling(refused, application + BootLayout.NESTED_SEPARATOR + LIBRARY_ENTRY);
    }

    /**
     * The same archive as the application itself. Expanding one into the positions a launcher would search
     * collects a name per entry, so the count answers first and the archive's own manifest is never read.
     */
    @Test
    void refusesACrowdedApplicationArchiveBeforeCollectingItsEntryNames() {
        Path application = AdversarialArchives.write(workspace, "crowded-app.jar", crowdedArchive());

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> EngineFixture.verify(List.of(application), List.of(), 17));

        assertNamesTheEntryCeiling(refused, application.toString());
    }

    private static void assertNamesTheEntryCeiling(IllegalStateException refused, String input) {
        assertTrue(refused.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES)),
                refused::getMessage);
        assertTrue(refused.getMessage().contains(input), refused::getMessage);
    }

    /**
     * One entry more than the ceiling allows, each of them empty, then a manifest no parser accepts. Built
     * once for this class: a quarter of a million entries is cheap to send and not quite free to write.
     */
    private static byte[] crowdedArchive() {
        if (crowded == null) {
            crowded = AdversarialArchives.archive(crowdedEntries(), Set.of());
        }
        return crowded;
    }

    private static Map<String, byte[]> crowdedEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(BootLayoutFixture.CLASSES_ROOT + CROWD_PREFIX + 0, new byte[0]);
        for (int index = 1; index <= ResourceBudget.MAXIMUM_ARCHIVE_ENTRIES; index++) {
            entries.put(CROWD_PREFIX + index, new byte[0]);
        }
        entries.put(JarFile.MANIFEST_NAME, UNPARSEABLE_MANIFEST.getBytes(StandardCharsets.UTF_8));
        return entries;
    }
}
