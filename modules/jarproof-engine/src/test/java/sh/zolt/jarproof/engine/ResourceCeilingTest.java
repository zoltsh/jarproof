package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The ceilings as the readers apply them, rather than as {@link ResourceBudget} states them.
 *
 * <p>{@link ResourceBudgetTest} proves each ceiling refuses what it says it refuses. What this measures
 * is whether the reader of every shape actually consults it: a class directory, an application archive
 * that carries its own dependencies, the index that orders those dependencies, a nested library, and the
 * finding list a whole run produced. A ceiling nobody charges is a ceiling that is not there, and the
 * shape whose reader forgot to charge it is exactly the shape an attacker would send.
 *
 * <p>Two levers keep these inputs small enough to be tests. A budget charged to its ceiling before the
 * read begins turns the next honest byte into a refusal, and a run of zeros deflates by roughly a
 * thousand to one, which is a compression ratio no real class file has.
 */
final class ResourceCeilingTest {
    private static final String CLASS_ENTRY = "com/acme/huge/Huge.class";
    private static final String INTERNAL_NAME = "com/acme/huge/Huge";
    private static final String LIBRARY_ENTRY = BootLayoutFixture.LIBRARY_DIRECTORY + "zeros.jar";
    private static final String NESTED_LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "orders-1.0.jar";
    private static final String RECORD_ENTRY = ArchiveLayout.MAVEN_PREFIX + "com.acme/orders/"
            + ArchiveLayout.POM_PROPERTIES_NAME;
    private static final String RECORD_TEXT = "groupId=com.acme\nartifactId=orders\nversion=1.0\n";
    private static final String ABSENT_PREFIX = "com/absent/A";
    private static final int COMPRESSIBLE_BYTES = 300_000;
    private static final int LOUD_REFERENCES = ResourceBudget.MAXIMUM_FINDINGS + 1;

    @TempDir
    Path workspace;

    @Test
    void refusesAClassDirectoryThatWouldPushTheRunPastTheByteCeiling() {
        Path directory = EngineFixture.classDirectory(workspace, "classes",
                EngineFixture.entries(CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME)));

        assertRefusesAtCeiling(directory, ResourceBudget.MAXIMUM_EXPANDED_BYTES);
    }

    @Test
    void refusesANestedApplicationIndexThatWouldPushTheRunPastTheByteCeiling() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME))
                .withLibrary(BootLayoutFixture.LIBRARY_DIRECTORY + "library.jar", EngineFixture.entries())
                .withIndex(BootLayoutFixture.INDEX_PATH, BootLayoutFixture.LIBRARY_DIRECTORY + "library.jar")
                .write(workspace, "indexed.jar");

        assertRefusesAtCeiling(application, ResourceBudget.MAXIMUM_EXPANDED_BYTES);
    }

    /**
     * A nested library that deflates a thousandfold never became a library. The ceiling is applied to
     * what the outer archive claims about that entry, before any of its bytes are read into memory,
     * because reading it whole is the one thing a nested layout has to do.
     */
    @Test
    void refusesANestedLibraryThatCompressesTooWellToBeReal() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME))
                .with(LIBRARY_ENTRY, storedZeros())
                .withIndex(BootLayoutFixture.INDEX_PATH, LIBRARY_ENTRY)
                .write(workspace, "compressible-library.jar");

        assertRefuses(application, ResourceBudget.MAXIMUM_COMPRESSION_RATIO);
    }

    /** The index that orders the nested libraries answers to the same ratio ceiling the libraries do. */
    @Test
    void refusesANestedApplicationIndexThatCompressesTooWellToBeReal() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME))
                .with(BootLayoutFixture.INDEX_PATH, new byte[COMPRESSIBLE_BYTES])
                .write(workspace, "compressible-index.jar");

        assertRefuses(application, ResourceBudget.MAXIMUM_COMPRESSION_RATIO);
    }

    /**
     * The bytes of a class entry inside a nested library are charged to the run like any other bytes, and
     * the refusal names that entry rather than the library it arrived in.
     *
     * <p>The budget here is charged to the ceiling less the library's own length, so reading the library
     * lands exactly on the ceiling and the first byte read out of it is the one that breaches. A full
     * pre-charge could not show this: it would refuse at the library itself and never reach inside.
     */
    @Test
    void refusesAClassEntryInsideANestedLibraryThatWouldPushTheRunPastTheByteCeiling() {
        byte[] library = BootLayoutFixture.jarBytes(
                EngineFixture.entries(CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME)));

        assertRefusesNaming(carrying(library, "byte-ceiling-class.jar"), library.length, CLASS_ENTRY);
    }

    /** A published record read out of the same library is charged to the same total and named the same way. */
    @Test
    void refusesAPublishedRecordInsideANestedLibraryThatWouldPushTheRunPastTheByteCeiling() {
        byte[] library = BootLayoutFixture.jarBytes(
                EngineFixture.entries(RECORD_ENTRY, RECORD_TEXT.getBytes(StandardCharsets.UTF_8)));

        assertRefusesNaming(carrying(library, "byte-ceiling-record.jar"), library.length, RECORD_ENTRY);
    }

    /**
     * The launcher index is charged too, and this application holds nothing else a reader would charge for:
     * no class files anywhere, and an index naming a library the archive does not carry. Reading the index
     * is therefore the only charge the run makes, so the ceiling can only be reached by that read.
     */
    @Test
    void refusesALauncherIndexThatWouldPushTheRunPastTheByteCeilingOnItsOwn()  {
        Path application = BootLayoutFixture.archive()
                .with(JarFile.MANIFEST_NAME, declaredAreas())
                .withIndex(BootLayoutFixture.INDEX_PATH, NESTED_LIBRARY)
                .write(workspace, "byte-ceiling-index.jar");

        assertRefusesNaming(application, 0, BootLayoutFixture.INDEX_PATH);
    }

    /**
     * A run that produced more findings than the ceiling allows is refused rather than reported. One
     * class naming twenty thousand absent types is a small artifact and an enormous report, which is the
     * asymmetry the ceiling exists for: the analysis is cheap, and rendering what it found is not.
     */
    @Test
    void refusesARunThatWouldReportMoreFindingsThanTheCeiling() {
        Path application = EngineFixture.jar(workspace, "loud.jar",
                EngineFixture.entries(CLASS_ENTRY, loudClass()));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EngineFixture.verify(List.of(application), List.of(), 17));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_FINDINGS)),
                failure.getMessage());
    }

    /**
     * Reads the application with the byte ceiling all but reached, and insists the refusal names the entry
     * that crossed it.
     *
     * @param application the artifact to read
     * @param allowance how many bytes to leave unspent, which is what the read must fit inside
     * @param entryName the entry whose bytes are expected to exhaust that allowance
     */
    private void assertRefusesNaming(Path application, long allowance, String entryName) {
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES - allowance, CLASS_ENTRY);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ArtifactCatalog.read(
                EngineFixture.request(List.of(application), List.of(), 17), budget));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_EXPANDED_BYTES)),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(entryName), failure.getMessage());
    }

    /** An application whose classes root is empty, so the library it carries is all a reader charges for. */
    private Path carrying(byte[] library, String name) {
        return BootLayoutFixture.archive()
                .with(JarFile.MANIFEST_NAME, declaredAreas())
                .withStoredLibrary(NESTED_LIBRARY, library)
                .write(workspace, name);
    }

    /** A manifest naming both nested areas, which is what makes an archive a layout without any entry. */
    private static byte[] declaredAreas() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(
                new Attributes.Name(BootLayoutFixture.DECLARED_CLASSES), BootLayoutFixture.CLASSES_ROOT);
        manifest.getMainAttributes().put(
                new Attributes.Name(BootLayoutFixture.DECLARED_LIBRARIES), BootLayoutFixture.LIBRARY_DIRECTORY);
        return EngineFixture.manifestBytes(manifest);
    }

    /** Reads the artifact with a budget already charged to the byte ceiling, so the next byte refuses. */
    private void assertRefusesAtCeiling(Path artifact, long ceiling) {
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES, CLASS_ENTRY);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ArtifactCatalog.read(
                EngineFixture.request(List.of(artifact), List.of(), 17), budget));

        assertTrue(failure.getMessage().contains(String.valueOf(ceiling)), failure.getMessage());
    }

    private void assertRefuses(Path artifact, long ceiling) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ArtifactCatalog.read(
                EngineFixture.request(List.of(artifact), List.of(), 17), new ResourceBudget()));

        assertTrue(failure.getMessage().contains(String.valueOf(ceiling)), failure.getMessage());
    }

    /** An archive whose one entry is stored rather than deflated, so the archive itself is a run of zeros. */
    private static byte[] storedZeros() {
        return AdversarialArchives.archive(
                EngineFixture.entries(CLASS_ENTRY, new byte[COMPRESSIBLE_BYTES]), Set.of(CLASS_ENTRY));
    }

    /**
     * One class whose handlers name more absent types than a run may report. Catch types are eight bytes
     * each in the exception table, so the loudest possible artifact is also one of the smallest.
     */
    private static byte[] loudClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, LinkageFixture.PUBLIC_CLASS, INTERNAL_NAME, null, EngineFixture.OBJECT, null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        for (int reference = 0; reference < LOUD_REFERENCES; reference++) {
            method.visitTryCatchBlock(start, end, handler, ABSENT_PREFIX + reference);
        }
        method.visitLabel(start);
        method.visitInsn(Opcodes.NOP);
        method.visitLabel(end);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
