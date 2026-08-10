package sh.zolt.jarproof.cli;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Builds real artifacts and runs the command line over them the way a user would. */
final class CliFixture {
    static final String OBJECT = "java/lang/Object";
    static final String VALIDATOR = "com/acme/app/OrderValidator";
    static final String POLICY = "com/acme/api/OrderPolicy";
    static final String CHECK = "check";
    static final String NO_ARGUMENTS = "()V";
    static final String TAKES_TEXT = "(Ljava/lang/String;)V";
    static final String APPLICATION = "--application";
    static final String CLASSPATH = "--classpath";
    static final String TARGET_JAVA = "--target-java";
    static final String JAVA_17 = "17";
    static final String FORMAT = "--format";
    static final String JSON = "json";
    static final String CLASS_SUFFIX = ".class";
    static final int JAVA_17_MAJOR = 61;

    private static final int PREVIEW_MINOR_VERSION = 0xFFFF;

    private CliFixture() {
    }

    /**
     * The flags a check of the broken application needs, followed by whatever a test adds.
     *
     * @param application the application artifact under test
     * @param classpath the one classpath entry it is verified against
     * @param extra flags this test adds
     * @return a complete command line
     */
    static String[] checkArgs(Path application, Path classpath, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                CHECK,
                APPLICATION,
                application.toString(),
                CLASSPATH,
                classpath.toString(),
                TARGET_JAVA,
                JAVA_17));
        args.addAll(List.of(extra));
        return args.toArray(new String[0]);
    }

    /** Runs one command line through the process entry point's test seam. */
    static Invocation invoke(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exitCode = Main.execute(new PrintWriter(out, true), new PrintWriter(err, true), args);
        return new Invocation(exitCode, text(out), text(err));
    }

    /**
     * An application whose bytecode calls a method the library on the classpath does not declare,
     * which is the DESIGN section 1 story and the one every end-to-end test is built on.
     */
    static Path brokenApplication(Path workspace) {
        return jar(workspace, "app.jar", entries(VALIDATOR + CLASS_SUFFIX, breakingCaller(VALIDATOR)));
    }

    /** A class whose one method calls the descriptor the library does not declare. */
    static byte[] breakingCaller(String internalName) {
        return caller(internalName, "validate", POLICY, CHECK, TAKES_TEXT);
    }

    /** The library that declares the same method name with a different descriptor. */
    static Path library(Path workspace) {
        return jar(workspace, "lib/api.jar", entries(POLICY + CLASS_SUFFIX, declaring(POLICY, CHECK, NO_ARGUMENTS)));
    }

    /** A class file declaring one method and nothing else. */
    static byte[] declaring(String internalName, String name, String descriptor) {
        ClassWriter writer = newClass(internalName, Opcodes.V17);
        writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A class file whose one method body names exactly one member of another class. */
    static byte[] caller(String internalName, String from, String owner, String name, String descriptor) {
        ClassWriter writer = newClass(internalName, Opcodes.V17);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, from, NO_ARGUMENTS, null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A class file that declares nothing, at the given class file version. */
    static byte[] classFile(String internalName, int version) {
        ClassWriter writer = newClass(internalName, version);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * A class file at the bundled release, marked as depending on that release's preview features.
     * ASM writes the version word as the minor version above the major one.
     */
    static byte[] previewClassFile(String internalName) {
        return classFile(internalName, PREVIEW_MINOR_VERSION << Short.SIZE | JAVA_17_MAJOR);
    }

    static Map<String, byte[]> entries(String name, byte[] content) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(name, content);
        return entries;
    }

    /** Writes an archive, creating any parent directories the name implies. */
    static Path jar(Path directory, String name, Map<String, byte[]> entries) {
        Path archive = directory.resolve(name);
        try {
            Files.createDirectories(archive.getParent());
            try (OutputStream file = Files.newOutputStream(archive);
                    ZipOutputStream zip = new ZipOutputStream(file)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return archive;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Drops the temporary-directory prefix a report legitimately contains, so a golden specimen can
     * name the artifacts the way a user working in their own project would see them.
     */
    static String rooted(String report, Path workspace) {
        return report.replace(workspace + File.separator, "").replace(File.separatorChar, '/');
    }

    private static ClassWriter newClass(String internalName, int version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, OBJECT, null);
        return writer;
    }

    private static String text(StringWriter written) {
        return written.toString().replace("\r\n", "\n");
    }

    /** One complete command-line run: what it returned, and what it wrote to each stream. */
    record Invocation(int exitCode, String out, String err) {
    }
}
