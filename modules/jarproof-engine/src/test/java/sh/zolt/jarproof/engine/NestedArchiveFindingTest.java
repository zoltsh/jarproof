package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

/** What JP3006 reports about a nested layout that does not hold together. */
final class NestedArchiveFindingTest {
    private static final String CODE = "JP3006";
    private static final String ORDERS = "com/acme/app/Orders";
    private static final String CODEC = "com/acme/lib/Codec";
    private static final String LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "codec.jar";
    private static final String ABSENT = BootLayoutFixture.LIBRARY_DIRECTORY + "absent.jar";
    private static final String APPLICATION = "app.jar";

    @TempDir
    Path workspace;

    @Test
    void reportsAnIndexEntryTheArchiveDoesNotHold() {
        Path application = layout().withIndex(BootLayoutFixture.INDEX_PATH, ABSENT, LIBRARY)
                .withLibrary(LIBRARY, classes(CODEC))
                .write(workspace, APPLICATION);

        Finding finding = EngineFixture.required(verify(application), CODE);

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(ABSENT, finding.subject());
        assertEquals(application.toString(), finding.artifact().artifact());
        assertEquals(Optional.empty(), finding.artifact().classEntry());
    }

    @Test
    void stillPlacesTheLibrariesTheIndexNamedCorrectly() {
        Path application = layout().withIndex(BootLayoutFixture.INDEX_PATH, ABSENT, LIBRARY)
                .withLibrary(LIBRARY, classes(CODEC))
                .write(workspace, APPLICATION);

        List<ClasspathEntry> entries = ClasspathExpander
                .expand(EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget())
                .entries();

        assertEquals(application + "!/" + LIBRARY, entries.get(1).display());
        assertEquals(3, entries.size());
    }

    @Test
    void reportsANestedLibraryThatWasCompressedRatherThanStored() {
        Path application = layout()
                .withCompressedLibrary(LIBRARY, classes(CODEC))
                .write(workspace, APPLICATION);

        Finding finding = EngineFixture.required(verify(application), CODE);

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(LIBRARY, finding.subject());
        assertEquals(application.toString(), finding.artifact().artifact());
    }

    @Test
    void readsACompressedNestedLibraryAnyway() {
        Path application = layout()
                .withCompressedLibrary(LIBRARY, classes(CODEC))
                .write(workspace, APPLICATION);

        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget());

        assertEquals(
                List.of(CODEC),
                catalog.artifacts().get(1).classes().stream().map(IndexedClass::internalName).toList());
    }

    @Test
    void reportsAnArchiveNestedInsideANestedLibrary() {
        Map<String, byte[]> deeper = EngineFixture.entries(
                "deeper/inner.jar", BootLayoutFixture.jarBytes(classes(CODEC)));
        Path application = layout().withLibrary(LIBRARY, deeper).write(workspace, APPLICATION);

        Finding finding = EngineFixture.required(verify(application), CODE);

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals("deeper/inner.jar", finding.subject());
        assertEquals(application.toString(), finding.artifact().artifact());
        assertEquals(LIBRARY, finding.artifact().classEntry().orElseThrow());
    }

    @Test
    void neverDescendsIntoTheArchiveItReported() {
        Map<String, byte[]> deeper = EngineFixture.entries(
                "deeper/inner.jar", BootLayoutFixture.jarBytes(classes(CODEC)));
        Path application = layout().withLibrary(LIBRARY, deeper).write(workspace, APPLICATION);

        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget());

        assertEquals(List.of(), catalog.artifacts().get(1).classes());
        assertEquals(3, catalog.artifacts().size());
    }

    @Test
    void refusesANestedLibraryTheArchiveNoLongerHolds() {
        Path application = layout().withLibrary(LIBRARY, classes(CODEC)).write(workspace, APPLICATION);
        ClasspathEntry moved = new ClasspathEntry(
                application + "!/" + ABSENT,
                application.toAbsolutePath().normalize(),
                EntryKind.NESTED_ARCHIVE,
                ClasspathOrigin.CLASSPATH,
                Optional.empty(),
                Optional.of(new NestedPosition(application.toString(), ABSENT)));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> NestedArchiveReader.read(moved, new ResourceBudget(), 17));

        assertTrue(failure.getMessage().contains(ABSENT), failure::getMessage);
    }

    @Test
    void chargesTheRunForEveryByteItReadOutOfTheArchive() {
        Path application = layout().withLibrary(LIBRARY, classes(CODEC)).write(workspace, APPLICATION);
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES, LIBRARY);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ArtifactCatalog.read(
                        EngineFixture.request(List.of(application), List.of(), 17), budget));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_EXPANDED_BYTES)),
                failure::getMessage);
    }

    @Test
    void refusesANestedEntryLargerThanItsOwnCeiling() {
        ResourceBudget budget = new ResourceBudget();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> budget.checkNestedEntryBytes(ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES + 1L, LIBRARY));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES)),
                failure::getMessage);
        assertEquals(
                LIBRARY,
                failure.getMessage().substring(failure.getMessage().length() - LIBRARY.length()));
    }

    private BootLayoutFixture layout() {
        return BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS));
    }

    private static Map<String, byte[]> classes(String internalName) {
        return EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName));
    }

    private List<Finding> verify(Path application) {
        return EngineFixture.verify(List.of(application), List.of(), 17);
    }
}
