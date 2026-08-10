package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** How an application archive that carries its own dependencies becomes classpath positions. */
final class NestedLayoutTest {
    private static final String ORDERS = "com/acme/app/Orders";
    private static final String CODEC = "com/acme/lib/Codec";
    private static final String LOADED = "org/springframework/boot/loader/JarLauncher";
    private static final String FIRST_LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "first.jar";
    private static final String SECOND_LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "second.jar";
    private static final String APPLICATION = "app.jar";

    @TempDir
    Path workspace;

    @Test
    void expandsTheAreasTheManifestDeclares() {
        Path application = BootLayoutFixture.archive()
                .with(JarFile.MANIFEST_NAME, EngineFixture.manifestBytes(declaring()))
                .with("classes/" + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .withLibrary("libs/codec.jar", classes(CODEC))
                .write(workspace, APPLICATION);

        List<ClasspathEntry> entries = expand(application);

        assertEquals(
                List.of(
                        application + "!/classes",
                        application + "!/libs/codec.jar",
                        application.toString()),
                displays(entries));
        assertEquals(
                List.of(EntryKind.NESTED_CLASSES, EntryKind.NESTED_ARCHIVE, EntryKind.HOST_ARCHIVE),
                entries.stream().map(ClasspathEntry::kind).toList());
    }

    @Test
    void expandsTheFatJarLayoutTheEntriesShowWhenTheManifestDeclaresNothing() {
        Path application = fatJar();

        List<ClasspathEntry> entries = expand(application);

        assertEquals(
                List.of(
                        application + "!/BOOT-INF/classes",
                        application + "!/" + FIRST_LIBRARY,
                        application.toString()),
                displays(entries));
        assertEquals(
                List.of(ClasspathOrigin.APPLICATION, ClasspathOrigin.CLASSPATH, ClasspathOrigin.CLASSPATH),
                entries.stream().map(ClasspathEntry::origin).toList());
    }

    @Test
    void expandsTheWarLayoutTheSameWay() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.WAR_CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .withIndex("WEB-INF/classpath.idx", BootLayoutFixture.WAR_LIBRARY_DIRECTORY + "codec.jar")
                .withLibrary(BootLayoutFixture.WAR_LIBRARY_DIRECTORY + "codec.jar", classes(CODEC))
                .write(workspace, "app.war");

        List<ClasspathEntry> entries = expand(application);

        assertEquals(
                List.of(
                        application + "!/WEB-INF/classes",
                        application + "!/" + BootLayoutFixture.WAR_LIBRARY_DIRECTORY + "codec.jar",
                        application.toString()),
                displays(entries));
    }

    @Test
    void readsTheClassesRootAsTheApplicationsOwnBytecode() {
        Path application = fatJar();

        IndexedArtifact classesRoot = read(application).artifacts().get(0);

        assertEquals(List.of(ORDERS), classesRoot.classes().stream().map(IndexedClass::internalName).toList());
        assertEquals(List.of(ORDERS + ".class"), classesRoot.classes().stream()
                .map(IndexedClass::entryName)
                .toList());
        assertEquals(Optional.empty(), classesRoot.coordinate());
    }

    @Test
    void readsEachNestedLibraryFromTheArchiveHoldingIt() {
        Path application = fatJar();

        IndexedArtifact library = read(application).artifacts().get(1);

        assertEquals(List.of(CODEC), library.classes().stream().map(IndexedClass::internalName).toList());
        assertEquals(application + "!/" + FIRST_LIBRARY, library.entry().display());
    }

    @Test
    void countsNoEntryOfANestedLibraryThatHoldsNoContent() {
        Map<String, byte[]> foldered = EngineFixture.entries();
        foldered.put("com/acme/lib/", new byte[0]);
        foldered.put(CODEC + ".class", EngineFixture.classFile(CODEC));
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .withLibrary(FIRST_LIBRARY, foldered)
                .write(workspace, APPLICATION);

        IndexedArtifact library = read(application).artifacts().get(1);

        assertEquals(List.of(CODEC), library.classes().stream().map(IndexedClass::internalName).toList());
    }

    @Test
    void readsTheArchiveItselfForItsOwnTopLevelClassesAlone() {
        Path application = fatJar();

        IndexedArtifact host = read(application).artifacts().get(2);

        assertEquals(List.of(LOADED), host.classes().stream().map(IndexedClass::internalName).toList());
    }

    @Test
    void leavesTheSameArchiveWholeWhenItIsOnlyAClasspathEntry() {
        Path application = fatJar();
        Path plain = EngineFixture.jar(workspace, "plain.jar",
                EngineFixture.entries(ORDERS + ".class", EngineFixture.classFile(ORDERS)));

        List<ClasspathEntry> entries = ClasspathExpander
                .expand(EngineFixture.request(List.of(plain), List.of(application), 17), new ResourceBudget())
                .entries();

        assertEquals(List.of(plain.toString(), application.toString()), displays(entries));
        assertEquals(List.of(EntryKind.ARCHIVE, EntryKind.ARCHIVE), entries.stream()
                .map(ClasspathEntry::kind)
                .toList());
    }

    @Test
    void ordersLibrariesByTheIndexRatherThanByTheArchive() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .withIndex(BootLayoutFixture.INDEX_PATH, SECOND_LIBRARY, FIRST_LIBRARY)
                .withLibrary(FIRST_LIBRARY, classes(CODEC))
                .withLibrary(SECOND_LIBRARY, classes("com/acme/lib/Second"))
                .write(workspace, APPLICATION);

        List<ClasspathEntry> entries = expand(application);

        assertEquals(
                List.of(
                        application + "!/BOOT-INF/classes",
                        application + "!/" + SECOND_LIBRARY,
                        application + "!/" + FIRST_LIBRARY,
                        application.toString()),
                displays(entries));
    }

    @Test
    void keepsALibraryTheIndexNeverNamed() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .withIndex(BootLayoutFixture.INDEX_PATH, SECOND_LIBRARY)
                .withLibrary(FIRST_LIBRARY, classes(CODEC))
                .withLibrary(SECOND_LIBRARY, classes("com/acme/lib/Second"))
                .write(workspace, APPLICATION);

        List<String> displays = displays(expand(application));

        assertEquals(application + "!/" + SECOND_LIBRARY, displays.get(1));
        assertEquals(application + "!/" + FIRST_LIBRARY, displays.get(2));
    }

    @Test
    void ignoresAnArchiveThatIsNotDirectlyInTheLibraryDirectory() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .withLibrary(BootLayoutFixture.LIBRARY_DIRECTORY + "deeper/codec.jar", classes(CODEC))
                .write(workspace, APPLICATION);

        assertEquals(2, expand(application).size());
    }

    @Test
    void ignoresAnEntryInTheLibraryDirectoryThatIsNoArchive() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .with(BootLayoutFixture.LIBRARY_DIRECTORY + "notes.txt", "notes".getBytes(StandardCharsets.UTF_8))
                .write(workspace, APPLICATION);

        assertEquals(2, expand(application).size());
    }

    @Test
    void expandsTheSameApplicationOnlyOnceWhenItIsNamedTwice() {
        Path application = fatJar();

        List<ClasspathEntry> entries = ClasspathExpander
                .expand(EngineFixture.request(List.of(application, application), List.of(), 17),
                        new ResourceBudget())
                .entries();

        assertEquals(3, entries.size());
        assertEquals(application + "!/BOOT-INF/classes", entries.get(0).display());
    }

    @Test
    void refusesAnApplicationThatIsNoArchiveAtAll() throws Exception {
        Path notAnArchive = Files.writeString(workspace.resolve("notes.txt"), "plain text");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ClasspathExpander.expand(
                        EngineFixture.request(List.of(notAnArchive), List.of(), 17), new ResourceBudget()));

        assertTrue(failure.getMessage().contains(notAnArchive.toString()), failure::getMessage);
    }

    private Path fatJar() {
        return BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS))
                .with(LOADED + ".class", EngineFixture.classFile(LOADED))
                .with("META-INF/NOTICE", "notice".getBytes(StandardCharsets.UTF_8))
                .withLibrary(FIRST_LIBRARY, classes(CODEC))
                .write(workspace, APPLICATION);
    }

    private static Manifest declaring() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(new Attributes.Name(BootLayoutFixture.DECLARED_CLASSES), "classes/");
        manifest.getMainAttributes().put(new Attributes.Name(BootLayoutFixture.DECLARED_LIBRARIES), "libs/");
        return manifest;
    }

    private static Map<String, byte[]> classes(String internalName) {
        return EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName));
    }

    private static List<String> displays(List<ClasspathEntry> entries) {
        return entries.stream().map(ClasspathEntry::display).toList();
    }

    private List<ClasspathEntry> expand(Path application) {
        return ClasspathExpander
                .expand(EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget())
                .entries();
    }

    private ArtifactCatalog read(Path application) {
        return ArtifactCatalog.read(
                EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget());
    }
}
