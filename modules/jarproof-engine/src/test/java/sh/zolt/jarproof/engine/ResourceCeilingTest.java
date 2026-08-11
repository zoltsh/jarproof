package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
