package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * An application that really uses Guava, Jackson, and Netty, written as bytecode.
 *
 * <p>The false-positive bar needs an application whose constant pool names third-party members for
 * real, and it needs the same application to run, so a zero-error report can be checked against the
 * corpus actually working rather than against a hope that it would. Every method here is
 * straight-line code that returns a value the caller prints, so the archive is both an analysis
 * input and a program.
 *
 * <p>The class files are written at the Java 17 level the corpus is checked against, so the
 * application itself can never be the thing that trips the class-file-version check.
 */
final class HealthyApplication {
    /** The class {@code java} is told to launch. */
    static final String MAIN_CLASS = "com.acme.corpus.LibraryTour";

    private static final String TOUR = "com/acme/corpus/LibraryTour";
    private static final String SERIALIZATION = "com/acme/corpus/Serialization";
    private static final String BUFFERS = "com/acme/corpus/Buffers";
    private static final String OBJECT = "java/lang/Object";
    private static final String IMMUTABLE_LIST = "com/google/common/collect/ImmutableList";
    private static final String OBJECT_MAPPER = "com/fasterxml/jackson/databind/ObjectMapper";
    private static final String UNPOOLED = "io/netty/buffer/Unpooled";
    private static final String BYTE_BUF = "io/netty/buffer/ByteBuf";
    private static final String PRINT_STREAM = "java/io/PrintStream";
    private static final String RETURNS_OBJECT = "()Ljava/lang/Object;";
    private static final String RETURNS_LIST = "()L" + IMMUTABLE_LIST + ";";
    private static final String RETURNS_BUFFER = "(I)L" + BYTE_BUF + ";";
    private static final String TAKES_OBJECT = "(Ljava/lang/Object;)V";
    private static final String ARCHIVE = "library-tour.jar";
    private static final String CLASS_SUFFIX = ".class";
    private static final String CHECKED = "java/lang/Exception";
    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

    private HealthyApplication() {
    }

    /**
     * Writes the application archive.
     *
     * @param directory the directory to write it into
     * @return the archive path
     */
    static Path jar(Path directory) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(TOUR + CLASS_SUFFIX, tour());
        entries.put(SERIALIZATION + CLASS_SUFFIX, serialization());
        entries.put(BUFFERS + CLASS_SUFFIX, buffers());
        return archive(directory.resolve(ARCHIVE), entries);
    }

    /** {@code main} prints what the other two classes produced, so the run proves they both ran. */
    private static byte[] tour() {
        ClassWriter writer = newClass(TOUR);
        MethodVisitor method = writer.visitMethod(
                PUBLIC_STATIC, "main", "([Ljava/lang/String;)V", null, new String[] {CHECKED});
        method.visitCode();
        printStream(method);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SERIALIZATION, "roundTrip", RETURNS_OBJECT, false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PRINT_STREAM, "println", TAKES_OBJECT, false);
        printStream(method);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, BUFFERS, "value", "()I", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PRINT_STREAM, "println", "(I)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Builds a Guava list and round-trips it through a Jackson mapper. */
    private static byte[] serialization() {
        ClassWriter writer = newClass(SERIALIZATION);
        MethodVisitor method =
                writer.visitMethod(PUBLIC_STATIC, "roundTrip", RETURNS_OBJECT, null, new String[] {CHECKED});
        method.visitCode();
        reversedList(method);
        roundTrip(method);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(3, 3);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Leaves a reversed immutable list of two strings in local 0. */
    private static void reversedList(MethodVisitor method) {
        method.visitLdcInsn("alpha");
        method.visitLdcInsn("beta");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                IMMUTABLE_LIST,
                "of",
                "(Ljava/lang/Object;Ljava/lang/Object;)L" + IMMUTABLE_LIST + ";",
                false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IMMUTABLE_LIST, "reverse", RETURNS_LIST, false);
        method.visitVarInsn(Opcodes.ASTORE, 0);
    }

    /** Writes local 0 as JSON and reads it back as a list, leaving the result on the stack. */
    private static void roundTrip(MethodVisitor method) {
        method.visitTypeInsn(Opcodes.NEW, OBJECT_MAPPER);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT_MAPPER, "<init>", "()V", false);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                OBJECT_MAPPER,
                "writeValueAsString",
                "(Ljava/lang/Object;)Ljava/lang/String;",
                false);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitLdcInsn(Type.getType("Ljava/util/List;"));
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                OBJECT_MAPPER,
                "readValue",
                "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
                false);
    }

    /** Writes an integer into a Netty buffer and reads it back out. */
    private static byte[] buffers() {
        ClassWriter writer = newClass(BUFFERS);
        MethodVisitor method = writer.visitMethod(PUBLIC_STATIC, "value", "()I", null, null);
        method.visitCode();
        method.visitIntInsn(Opcodes.BIPUSH, 8);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, UNPOOLED, "buffer", RETURNS_BUFFER, false);
        method.visitVarInsn(Opcodes.ASTORE, 0);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitIntInsn(Opcodes.BIPUSH, 7);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "writeInt", RETURNS_BUFFER, false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "readInt", "()I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void printStream(MethodVisitor method) {
        method.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "L" + PRINT_STREAM + ";");
    }

    private static ClassWriter newClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                internalName,
                null,
                OBJECT,
                null);
        return writer;
    }

    private static Path archive(Path path, Map<String, byte[]> entries) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream file = Files.newOutputStream(path);
                    ZipOutputStream zip = new ZipOutputStream(file)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return path;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
