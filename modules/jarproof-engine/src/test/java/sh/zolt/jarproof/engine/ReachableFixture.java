package sh.zolt.jarproof.engine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Builds classes whose individual methods reach for exactly what a reachability test needs.
 *
 * <p>{@link LinkageFixture} writes one {@code run()V} per class, which is the right shape for asking
 * whether a reference links. A call graph needs the other axis: several methods in one class, only some
 * of them reachable, so a test can prove that a break in one is reported and a break in the next is
 * omitted. Every builder here therefore takes a map from method signature to the references that
 * method's body names, and emits a superclass constructor call for a constructor so an allocation looks
 * the way a compiler writes one.
 *
 * <p>Bodies are not verifiable bytecode — a reference is emitted without the stack a real instruction
 * would need — for the same reason {@link LinkageFixture}'s are not: nothing here is ever executed, and
 * the engine reads class files rather than running them.
 */
final class ReachableFixture {
    static final String MAIN = "main([Ljava/lang/String;)V";
    static final String WORK = "work()V";
    static final String UNUSED = "unused()V";
    static final String HANDLE = "handle()V";
    static final String CONSTRUCTOR = "<init>()V";
    static final String INITIALIZER = "<clinit>()V";
    static final String CAPTURE = "capture()V";
    static final String LAMBDA_BODY = "lambda$capture$0()V";
    static final String HASH_CODE = "hashCode()I";
    static final String FLAG = "FLAG";
    static final String INT_FIELD = "I";

    private static final JdkSymbolCatalog PLATFORM = JdkSymbolCatalog.forRelease(17);
    private static final String LAMBDA_FACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String LAMBDA_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;";
    private static final String NO_ARGUMENTS = "()V";
    private static final Set<String> INSTANCE_METHODS = Set.of(CONSTRUCTOR, HANDLE, HASH_CODE);
    private static final Map<ReferenceKind, Integer> OPCODES = Map.of(
            ReferenceKind.GET_STATIC, Opcodes.GETSTATIC,
            ReferenceKind.INVOKE_VIRTUAL, Opcodes.INVOKEVIRTUAL,
            ReferenceKind.INVOKE_SPECIAL, Opcodes.INVOKESPECIAL,
            ReferenceKind.INVOKE_STATIC, Opcodes.INVOKESTATIC,
            ReferenceKind.INVOKE_INTERFACE, Opcodes.INVOKEINTERFACE);

    private ReachableFixture() {
    }

    /** One method body: the signature, and the references its bytecode names. */
    static Map<String, List<MemberReference>> body(String signature, MemberReference... references) {
        return and(new LinkedHashMap<>(), signature, references);
    }

    /** Another method body on the same class. */
    static Map<String, List<MemberReference>> and(
            Map<String, List<MemberReference>> bodies, String signature, MemberReference... references) {
        bodies.put(signature, List.of(references));
        return bodies;
    }

    /** A static call to a no-argument method. */
    static MemberReference call(String owner, String signature) {
        return reference(ReferenceKind.INVOKE_STATIC, owner, signature);
    }

    /** A virtual call, which is the reference rapid type analysis has to resolve per receiver. */
    static MemberReference dispatch(String owner, String signature) {
        return reference(ReferenceKind.INVOKE_VIRTUAL, owner, signature);
    }

    /** The same call written through an interface, which is the other half of the dispatch rule. */
    static MemberReference dispatchThrough(String owner, String signature) {
        return reference(ReferenceKind.INVOKE_INTERFACE, owner, signature);
    }

    /** An allocation, written the way {@code new} is: a special call to the owner's constructor. */
    static MemberReference construct(String owner) {
        return reference(ReferenceKind.INVOKE_SPECIAL, owner, CONSTRUCTOR);
    }

    /** A static field read, which forces the declaring class to initialize. */
    static MemberReference read(String owner) {
        return new MemberReference(
                ReferenceKind.GET_STATIC, owner, FLAG, INT_FIELD, MAIN, Optional.empty());
    }

    /** A class with the given superclass and interfaces, whose methods name the references given. */
    static byte[] type(
            String internalName,
            String superName,
            List<String> interfaces,
            Map<String, List<MemberReference>> bodies) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, LinkageFixture.PUBLIC_CLASS, internalName, null, superName,
                interfaces.toArray(new String[0]));
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, FLAG, INT_FIELD, null, null).visitEnd();
        bodies.forEach((signature, references) -> body(writer, superName, signature, references));
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A class extending {@code Object}. */
    static byte[] type(String internalName, Map<String, List<MemberReference>> bodies) {
        return type(internalName, EngineFixture.OBJECT, List.of(), bodies);
    }

    /**
     * A class holding one lambda capture site and the synthetic body it names.
     *
     * <p>The body is reached only through a bootstrap argument of the {@code invokedynamic}, which is
     * the whole point: no call instruction anywhere names it.
     */
    static byte[] lambdaHolder(String internalName, List<MemberReference> lambdaBody) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, LinkageFixture.PUBLIC_CLASS, internalName, null,
                EngineFixture.OBJECT, null);
        capture(writer, internalName);
        body(writer, EngineFixture.OBJECT, LAMBDA_BODY, lambdaBody);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * A class whose {@code work()V} wraps its references in a handler for one throwable type.
     *
     * <p>The handler is what makes the method a linkage probe when the type it catches is a linkage
     * failure, so this is the one builder whose bytecode shape rather than whose references is the
     * subject of the test.
     */
    static byte[] probe(String internalName, String caught, List<MemberReference> references) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, LinkageFixture.PUBLIC_CLASS, internalName, null,
                EngineFixture.OBJECT, null);
        int split = WORK.indexOf('(');
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                WORK.substring(0, split), WORK.substring(split), null, null);
        method.visitCode();
        guarded(method, caught, references);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** The call graph of one classpath, so a test can ask what it proved and how. */
    static ReachableMethods graph(List<Path> applications, List<Path> classpath) {
        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(applications, classpath, 17), new ResourceBudget());
        return Reachability.of(catalog, new ResolutionTable(catalog, PLATFORM));
    }

    private static MemberReference reference(ReferenceKind kind, String owner, String signature) {
        int split = signature.indexOf('(');
        return new MemberReference(
                kind,
                owner,
                signature.substring(0, split),
                signature.substring(split),
                MAIN,
                Optional.empty());
    }

    private static void capture(ClassWriter writer, String internalName) {
        int split = CAPTURE.indexOf('(');
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                CAPTURE.substring(0, split),
                CAPTURE.substring(split),
                null,
                null);
        method.visitCode();
        method.visitInvokeDynamicInsn(
                "run",
                "()Ljava/lang/Runnable;",
                new Handle(Opcodes.H_INVOKESTATIC, LAMBDA_FACTORY, "metafactory", LAMBDA_DESCRIPTOR, false),
                Type.getMethodType(NO_ARGUMENTS),
                new Handle(Opcodes.H_INVOKESTATIC, internalName, name(LAMBDA_BODY), NO_ARGUMENTS, false),
                Type.getMethodType(NO_ARGUMENTS));
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
    }

    private static void body(
            ClassWriter writer, String superName, String signature, List<MemberReference> references) {
        int split = signature.indexOf('(');
        MethodVisitor method = writer.visitMethod(
                access(signature), signature.substring(0, split), signature.substring(split), null, null);
        method.visitCode();
        if (signature.equals(CONSTRUCTOR)) {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, name(CONSTRUCTOR), NO_ARGUMENTS, false);
        }
        references.forEach(reference -> emit(method, reference));
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
    }

    private static void guarded(MethodVisitor method, String caught, List<MemberReference> references) {
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitTryCatchBlock(start, end, handler, caught);
        method.visitLabel(start);
        references.forEach(reference -> emit(method, reference));
        method.visitLabel(end);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.POP);
    }

    private static void emit(MethodVisitor method, MemberReference reference) {
        if (reference.kind() == ReferenceKind.GET_STATIC) {
            method.visitFieldInsn(
                    Opcodes.GETSTATIC, reference.ownerInternalName(), reference.name(), reference.descriptor());
            return;
        }
        method.visitMethodInsn(
                OPCODES.get(reference.kind()),
                reference.ownerInternalName(),
                reference.name(),
                reference.descriptor(),
                reference.kind() == ReferenceKind.INVOKE_INTERFACE);
    }

    private static int access(String signature) {
        if (signature.equals(INITIALIZER)) {
            return Opcodes.ACC_STATIC;
        }
        return INSTANCE_METHODS.contains(signature)
                ? Opcodes.ACC_PUBLIC
                : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
    }

    private static String name(String signature) {
        return signature.substring(0, signature.indexOf('('));
    }
}
