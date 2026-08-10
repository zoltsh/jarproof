package sh.zolt.jarproof.engine;

import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Builds classes carrying the debug information a compiler emits by default: a {@code SourceFile}
 * attribute, and a line number marking each instruction.
 *
 * <p>{@link ReferenceFixture} and {@link LinkageFixture} deliberately write neither, which is the
 * other half of the contract — a class compiled without debug information must report no source and
 * no line rather than an invented one — so the traced shapes live here instead of as a variation of
 * theirs.
 *
 * <p>Lines are spaced rather than consecutive so an assertion cannot pass by accident: a reference
 * recorded at the wrong marker lands nowhere near the expected number.
 */
final class SourceDebugFixture {
    static final String SOURCE_FILE = "Caller.java";
    static final int FIRST_LINE = 12;

    private static final int LINE_STEP = 5;

    private SourceDebugFixture() {
    }

    /**
     * The line the reference at one position is written on.
     *
     * @param position index of the reference in the list handed to a traced builder
     * @return the line number that builder marks it with
     */
    static int lineOf(int position) {
        return FIRST_LINE + position * LINE_STEP;
    }

    /** A caller whose class file names its source file and a line for every member reference. */
    static byte[] tracedCaller(String internalName, List<MemberReference> references) {
        ClassWriter writer = open(internalName);
        MethodVisitor method = beginRun(writer);
        for (int index = 0; index < references.size(); index++) {
            mark(method, lineOf(index));
            invoke(method, references.get(index));
        }
        return close(writer, method);
    }

    /** A caller whose class file names its source file and a line for every type instruction. */
    static byte[] tracedTypeCaller(String internalName, List<String> types) {
        ClassWriter writer = open(internalName);
        MethodVisitor method = beginRun(writer);
        for (int index = 0; index < types.size(); index++) {
            mark(method, lineOf(index));
            method.visitTypeInsn(Opcodes.NEW, types.get(index));
            method.visitInsn(Opcodes.POP);
        }
        return close(writer, method);
    }

    /** A caller compiled with a source file name and no line table, which {@code -g:source} produces. */
    static byte[] untracedCaller(String internalName, List<MemberReference> references) {
        ClassWriter writer = open(internalName);
        MethodVisitor method = beginRun(writer);
        references.forEach(reference -> invoke(method, reference));
        return close(writer, method);
    }

    private static ClassWriter open(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17, LinkageFixture.PUBLIC_CLASS, internalName, null, EngineFixture.OBJECT, null);
        writer.visitSource(SOURCE_FILE, null);
        return writer;
    }

    private static MethodVisitor beginRun(ClassWriter writer) {
        int split = LinkageFixture.RUN.indexOf('(');
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                LinkageFixture.RUN.substring(0, split),
                LinkageFixture.RUN.substring(split),
                null,
                null);
        method.visitCode();
        return method;
    }

    private static void mark(MethodVisitor method, int line) {
        Label at = new Label();
        method.visitLabel(at);
        method.visitLineNumber(line, at);
    }

    private static void invoke(MethodVisitor method, MemberReference reference) {
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                reference.ownerInternalName(),
                reference.name(),
                reference.descriptor(),
                false);
    }

    private static byte[] close(ClassWriter writer, MethodVisitor method) {
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
