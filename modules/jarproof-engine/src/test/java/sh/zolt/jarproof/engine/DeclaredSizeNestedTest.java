package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The same claim, made about an entry of a nested layout.
 *
 * <p>An application archive that carries its own dependencies asks a reader to hold whole entries in
 * memory: a library archive, the index that orders those libraries, and — once inside a library that never
 * became a file — its manifest and its published records. Bytes that never became a file cannot be
 * addressed any other way, which is exactly why each of those reads is bounded, and why every one of them
 * is bounded on the claim first: an entry that declares a quarter of a gigabyte is refused for declaring
 * it, whether the declaration is true or not.
 */
final class DeclaredSizeNestedTest {
    private static final String INTERNAL_NAME = "com/acme/orders/Widget";
    private static final String CLASS_ENTRY = INTERNAL_NAME + ArchiveLayout.CLASS_SUFFIX;
    private static final String LIBRARY_ENTRY = BootLayoutFixture.LIBRARY_DIRECTORY + "orders-1.0.jar";
    private static final String RECORD_ENTRY = ArchiveLayout.MAVEN_PREFIX + "com.acme/orders/"
            + ArchiveLayout.POM_PROPERTIES_NAME;
    private static final String RECORD_TEXT = "groupId=com.acme\nartifactId=orders\nversion=1.0\n";
    private static final long BEYOND_THE_NESTED_CEILING = ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES + 1L;
    private static final long BEYOND_THE_CLASS_CEILING = ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1L;

    @TempDir
    Path workspace;

    /**
     * A library entry claiming a quarter of a gigabyte is refused before it is read into memory, which is
     * the one thing reading a nested layout has to do and therefore the one thing worth bounding first.
     */
    @Test
    void refusesANestedLibraryThatDeclaresMoreBytesThanANestedEntryMayOccupy() {
        byte[] library = BootLayoutFixture.jarBytes(
                EngineFixture.entries(CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME)));
        Path application = overstating("overstated-library.jar", LIBRARY_ENTRY, library,
                BEYOND_THE_NESTED_CEILING);

        assertNamesTheCeiling(refusal(application), ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES,
                application + BootLayout.NESTED_SEPARATOR + LIBRARY_ENTRY);
    }

    /** The index that orders the libraries is read whole as well, so it answers to the same claim. */
    @Test
    void refusesALauncherIndexThatDeclaresMoreBytesThanANestedEntryMayOccupy() {
        byte[] index = ("- \"" + LIBRARY_ENTRY + "\"\n").getBytes(StandardCharsets.UTF_8);
        Path application = overstating("overstated-index.jar", BootLayoutFixture.INDEX_PATH, index,
                BEYOND_THE_NESTED_CEILING);

        assertNamesTheCeiling(refusal(application), ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES,
                BootLayoutFixture.INDEX_PATH);
    }

    /**
     * A published record inside a library is what proves the library's publication identity, so the walk
     * reads it as it passes it — through the ceiling on an entry read whole, on the claim, from a stream
     * that has not yet produced a byte of it.
     */
    @Test
    void refusesAPublishedRecordInsideALibraryThatDeclaresTooManyBytes() {
        Path application = carrying("overstated-record.jar", RECORD_ENTRY,
                RECORD_TEXT.getBytes(StandardCharsets.UTF_8), BEYOND_THE_NESTED_CEILING);

        assertNamesTheCeiling(refusal(application), ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES, RECORD_ENTRY);
    }

    /**
     * A library's own manifest decides its multi-release selection, so the walk reads that too — and it is
     * read the way the class entries beside it are read, which is the ceiling that refuses it.
     */
    @Test
    void refusesAManifestInsideALibraryThatDeclaresTooManyBytes() {
        Path application = carrying("overstated-manifest.jar", JarFile.MANIFEST_NAME,
                EngineFixture.manifestBytes(EngineFixture.manifest()), BEYOND_THE_CLASS_CEILING);

        assertNamesTheCeiling(
                refusal(application), ResourceBudget.MAXIMUM_CLASS_FILE_BYTES, JarFile.MANIFEST_NAME);
    }

    /**
     * A class entry inside a library declares no size at all, and delivers more than a class file may
     * occupy. An entry read from a stream may not know its size until it has been read — the local header
     * of a deflated entry carries none — so there is no claim to refuse it on, and the ceiling on what
     * actually arrived is the only one that can answer. This is why every ceiling here is applied twice.
     */
    @Test
    void refusesAClassEntryInsideALibraryThatDeliversMoreThanAClassFileMayOccupy() {
        byte[] library = BootLayoutFixture.jarBytes(
                EngineFixture.entries(CLASS_ENTRY, new byte[ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1]));
        Path application = BootLayoutFixture.archive()
                .withStoredLibrary(LIBRARY_ENTRY, library)
                .with(BootLayoutFixture.CLASSES_ROOT + CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME))
                .write(workspace, "understated-library.jar");

        assertNamesTheCeiling(refusal(application), ResourceBudget.MAXIMUM_CLASS_FILE_BYTES, CLASS_ENTRY);
    }

    private static void assertNamesTheCeiling(IllegalStateException refused, long ceiling, String input) {
        assertTrue(refused.getMessage().contains(String.valueOf(ceiling)), refused::getMessage);
        assertTrue(refused.getMessage().contains(input), refused::getMessage);
    }

    private static IllegalStateException refusal(Path application) {
        return assertThrows(IllegalStateException.class,
                () -> EngineFixture.verify(List.of(application), List.of(), 17));
    }

    /** An application archive whose named entry is stored and declares a size nobody stored. */
    private Path overstating(String name, String entryName, byte[] content, long declared) {
        Map<String, byte[]> entries = EngineFixture.entries(
                BootLayoutFixture.CLASSES_ROOT + CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME));
        entries.put(entryName, content);
        byte[] archive = AdversarialArchives.archive(entries, Set.of(entryName));
        return AdversarialArchives.write(workspace, name,
                AdversarialArchives.overstating(archive, content.length, declared));
    }

    /** An application archive carrying a library whose one entry declares a size nobody stored. */
    private Path carrying(String name, String entryName, byte[] content, long declared) {
        byte[] library = AdversarialArchives.overstating(
                AdversarialArchives.archive(EngineFixture.entries(entryName, content), Set.of(entryName)),
                content.length,
                declared);
        return BootLayoutFixture.archive()
                .withStoredLibrary(LIBRARY_ENTRY, library)
                .with(BootLayoutFixture.CLASSES_ROOT + CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME))
                .write(workspace, name);
    }
}
