package sh.zolt.jarproof.engine;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Builds one class whose bytecode reaches for every reference shape the engine indexes. */
final class ReferenceFixture {
    static final String OWNER = "com/acme/orders/OrderValidator";
    static final String TARGET = "com/acme/orders/Widget";
    static final String CATCH_TYPE = "java/lang/IllegalStateException";
    static final String ARRAY_TARGET = "com/acme/orders/Cell";
    static final String MULTI_ARRAY_TARGET = "com/acme/orders/Grid";
    static final String CONSTANT_TARGET = "com/acme/orders/Ledger";
    static final String DEEPEST_CONSTANT_OWNER = "com/acme/orders/Depth8";
    static final String BEYOND_DEPTH_OWNER = "com/acme/orders/Depth9";
    static final String NEST_HOST = "com/acme/orders/Host";
    static final String NEST_MEMBER = "com/acme/orders/Host$Inner";
    static final String BOOTSTRAP_OWNER = "com/acme/orders/Bootstraps";
    static final String CONSTANT_OWNER = "com/acme/orders/Constants";
    static final String NESTED_CONSTANT_OWNER = "com/acme/orders/Deep";
    static final String HANDLE_OWNER = "com/acme/orders/Handles";
    static final String METHOD = "run()V";

    private static final String BOOTSTRAP_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;";
    private static final String CONSTANT_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)I";
    private static final int CONSTANT_DEPTH_CEILING = 8;
    private static final String DEPTH_OWNER_PREFIX = "com/acme/orders/Depth";

    private ReferenceFixture() {
    }

    static byte[] classFile() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                OWNER,
                null,
                EngineFixture.OBJECT,
                new String[] {"java/lang/Runnable"});
        writer.visitNestHost(NEST_HOST);
        writer.visitNestMember(NEST_MEMBER);
        writer.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd();
        body(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void body(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        method.visitCode();
        instructions(method);
        constants(method);
        catchBlock(method);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(6, 2);
        method.visitEnd();
    }

    private static void instructions(MethodVisitor method) {
        method.visitTypeInsn(Opcodes.NEW, TARGET);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", "()V", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "FLAG", "Z");
        method.visitInsn(Opcodes.POP);
        method.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "FLAG", "Z");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, "reset", "()V", false);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitTypeInsn(Opcodes.ANEWARRAY, ARRAY_TARGET);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitTypeInsn(Opcodes.ANEWARRAY, "[L" + ARRAY_TARGET + ";");
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMultiANewArrayInsn("[[L" + MULTI_ARRAY_TARGET + ";", 2);
        method.visitInsn(Opcodes.POP);
    }

    private static void constants(MethodVisitor method) {
        method.visitLdcInsn(Type.getObjectType(CONSTANT_TARGET));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn("a plain string constant reaches for nothing");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(Type.getType("[I"));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(handle(HANDLE_OWNER, "chosen"));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(deepConstant());
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(depthChain());
        method.visitInsn(Opcodes.POP);
        method.visitInvokeDynamicInsn(
                "apply",
                "()V",
                new Handle(Opcodes.H_INVOKESTATIC, BOOTSTRAP_OWNER, "factory", BOOTSTRAP_DESCRIPTOR, false),
                deepConstant());
    }

    private static ConstantDynamic deepConstant() {
        ConstantDynamic nested = new ConstantDynamic(
                "deep",
                "I",
                new Handle(Opcodes.H_INVOKESTATIC, NESTED_CONSTANT_OWNER, "compute", CONSTANT_DESCRIPTOR, false));
        return new ConstantDynamic(
                "outer",
                "I",
                new Handle(Opcodes.H_INVOKESTATIC, CONSTANT_OWNER, "compute", CONSTANT_DESCRIPTOR, false),
                nested);
    }

    /**
     * A chain of dynamic constants one level deeper than the collector follows, each level naming a
     * bootstrap method of its own.
     *
     * <p>A constant that reaches through another constant is how a compiled condy nest arrives, and the
     * ceiling on how far that is followed is a guard against a hostile constant pool rather than a shape
     * any compiler emits. Naming every level separately is what lets a test say which levels were read:
     * the deepest one inside the ceiling, and the first one past it.
     */
    private static ConstantDynamic depthChain() {
        ConstantDynamic constant = new ConstantDynamic(
                "depth", "I", depthHandle(CONSTANT_DEPTH_CEILING + 1));
        for (int level = CONSTANT_DEPTH_CEILING; level >= 0; level--) {
            constant = new ConstantDynamic("depth", "I", depthHandle(level), constant);
        }
        return constant;
    }

    private static Handle depthHandle(int level) {
        return new Handle(
                Opcodes.H_INVOKESTATIC, DEPTH_OWNER_PREFIX + level, "compute", CONSTANT_DESCRIPTOR, false);
    }

    private static Handle handle(String owner, String name) {
        return new Handle(Opcodes.H_INVOKESTATIC, owner, name, "()V", false);
    }

    private static void catchBlock(MethodVisitor method) {
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        Label cleanup = new Label();
        method.visitTryCatchBlock(start, end, handler, CATCH_TYPE);
        method.visitTryCatchBlock(start, end, cleanup, null);
        method.visitLabel(start);
        method.visitInsn(Opcodes.NOP);
        method.visitLabel(end);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.POP);
        method.visitLabel(cleanup);
        method.visitInsn(Opcodes.POP);
    }
}
