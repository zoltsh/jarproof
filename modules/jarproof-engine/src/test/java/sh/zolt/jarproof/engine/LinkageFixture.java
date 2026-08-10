package sh.zolt.jarproof.engine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/** Builds callers, targets, and nests whose bytecode reaches for exactly what a linkage test needs. */
final class LinkageFixture {
    static final String CALLER = "com/acme/app/Caller";
    static final String RUN = "run()V";
    static final String AGAIN = "again()V";
    static final String LAMBDA_BODY = "lambda$run$0";
    static final int PUBLIC_CLASS = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
    static final int PACKAGE_CLASS = Opcodes.ACC_SUPER;
    static final int PUBLIC_INTERFACE = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
    static final int METAFACTORY = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

    private static final JdkSymbolCatalog PLATFORM = JdkSymbolCatalog.forRelease(17);
    private static final String LAMBDA_FACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String LAMBDA_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;";
    private static final Map<ReferenceKind, Integer> FIELD_OPCODES = Map.of(
            ReferenceKind.GET_STATIC, Opcodes.GETSTATIC,
            ReferenceKind.PUT_STATIC, Opcodes.PUTSTATIC,
            ReferenceKind.GET_FIELD, Opcodes.GETFIELD,
            ReferenceKind.PUT_FIELD, Opcodes.PUTFIELD);
    private static final Map<ReferenceKind, Integer> INVOKE_OPCODES = Map.of(
            ReferenceKind.INVOKE_VIRTUAL, Opcodes.INVOKEVIRTUAL,
            ReferenceKind.INVOKE_SPECIAL, Opcodes.INVOKESPECIAL,
            ReferenceKind.INVOKE_STATIC, Opcodes.INVOKESTATIC,
            ReferenceKind.INVOKE_INTERFACE, Opcodes.INVOKEINTERFACE);

    private LinkageFixture() {
    }

    static MemberShape member(String name, String descriptor, int access) {
        return new MemberShape(name, descriptor, access);
    }

    static MemberReference reference(ReferenceKind kind, String owner, String name, String descriptor) {
        return new MemberReference(kind, owner, name, descriptor, RUN);
    }

    /** A target type declaring exactly the given members and nothing else. */
    static byte[] type(String internalName, int access, String superName, List<String> interfaces,
            List<MemberShape> members) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, access, internalName, null, superName, interfaces.toArray(new String[0]));
        members.forEach(shape -> declare(writer, shape));
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A caller extending {@code Object} whose {@code run()V} body names each reference. */
    static byte[] caller(String internalName, List<MemberReference> references) {
        return caller(internalName, EngineFixture.OBJECT, references);
    }

    /** A caller extending the given superclass whose {@code run()V} body names each reference. */
    static byte[] caller(String internalName, String superName, List<MemberReference> references) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, PUBLIC_CLASS, internalName, null, superName, null);
        body(writer, RUN, references, List.of());
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A caller whose {@code run()V} body names each class with a type instruction. */
    static byte[] typeCaller(String internalName, List<String> types) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, PUBLIC_CLASS, internalName, null, EngineFixture.OBJECT, null);
        body(writer, RUN, List.of(), types);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A caller that names one reference from two separate methods, {@code run} first. */
    static byte[] repeatedCaller(String internalName, MemberReference reference) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, PUBLIC_CLASS, internalName, null, EngineFixture.OBJECT, null);
        body(writer, RUN, List.of(reference, reference), List.of());
        body(writer, AGAIN, List.of(reference), List.of());
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A nest host that declares its members and reaches for theirs. */
    static byte[] nestHost(String internalName, List<String> nestMembers, List<MemberReference> references) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, PUBLIC_CLASS, internalName, null, EngineFixture.OBJECT, null);
        nestMembers.forEach(writer::visitNestMember);
        body(writer, RUN, references, List.of());
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A nested class that claims a host and declares the members the host reaches for. */
    static byte[] nestMember(String internalName, String host, List<MemberShape> members) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, PACKAGE_CLASS, internalName, null, EngineFixture.OBJECT, null);
        writer.visitNestHost(host);
        members.forEach(shape -> declare(writer, shape));
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A caller holding one lambda call site: a bootstrap handle plus a handle to its own body. */
    static byte[] lambdaCaller(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, PUBLIC_CLASS, internalName, null, EngineFixture.OBJECT, null);
        writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                LAMBDA_BODY, "()V", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        method.visitCode();
        method.visitInvokeDynamicInsn(
                "run",
                "()Ljava/lang/Runnable;",
                new Handle(Opcodes.H_INVOKESTATIC, LAMBDA_FACTORY, "metafactory", LAMBDA_DESCRIPTOR, false),
                Type.getMethodType("()V"),
                new Handle(Opcodes.H_INVOKESTATIC, internalName, LAMBDA_BODY, "()V", false),
                Type.getMethodType("()V"));
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    static Map<String, byte[]> classes(String internalName, byte[] classFile) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(internalName + ArchiveLayout.CLASS_SUFFIX, classFile);
        return entries;
    }

    static Map<String, byte[]> and(Map<String, byte[]> entries, String internalName, byte[] classFile) {
        entries.put(internalName + ArchiveLayout.CLASS_SUFFIX, classFile);
        return entries;
    }

    /** Runs the linkage check over a freshly read catalog of the given classpath. */
    static List<Finding> check(List<Path> applications, List<Path> classpath, Scope scope) {
        return LinkageCheck.run(catalog(applications, classpath), PLATFORM, scope);
    }

    /** A resolution table over the given classpath, backed by the bundled Java 17 runtime profile. */
    static ResolutionTable table(List<Path> applications, List<Path> classpath) {
        return new ResolutionTable(catalog(applications, classpath), PLATFORM);
    }

    private static ArtifactCatalog catalog(List<Path> applications, List<Path> classpath) {
        return ArtifactCatalog.read(
                EngineFixture.request(applications, classpath, 17), new ResourceBudget());
    }

    private static void declare(ClassWriter writer, MemberShape shape) {
        if (shape.descriptor().charAt(0) == '(') {
            writer.visitMethod(shape.accessFlags(), shape.name(), shape.descriptor(), null, null).visitEnd();
            return;
        }
        writer.visitField(shape.accessFlags(), shape.name(), shape.descriptor(), null, null).visitEnd();
    }

    private static void body(ClassWriter writer, String signature, List<MemberReference> references,
            List<String> types) {
        int split = signature.indexOf('(');
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, signature.substring(0, split), signature.substring(split), null, null);
        method.visitCode();
        types.forEach(type -> method.visitTypeInsn(Opcodes.NEW, type));
        references.forEach(reference -> emit(method, reference));
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
    }

    private static void emit(MethodVisitor method, MemberReference reference) {
        Integer field = FIELD_OPCODES.get(reference.kind());
        if (field != null) {
            method.visitFieldInsn(field, reference.ownerInternalName(), reference.name(), reference.descriptor());
            return;
        }
        Integer invoke = INVOKE_OPCODES.get(reference.kind());
        if (invoke != null) {
            method.visitMethodInsn(invoke, reference.ownerInternalName(), reference.name(), reference.descriptor(),
                    reference.kind() == ReferenceKind.INVOKE_INTERFACE);
            return;
        }
        method.visitLdcInsn(new Handle(handleTag(reference.descriptor()), reference.ownerInternalName(),
                reference.name(), reference.descriptor(), false));
    }

    private static int handleTag(String descriptor) {
        return descriptor.charAt(0) == '(' ? Opcodes.H_INVOKESTATIC : Opcodes.H_GETSTATIC;
    }
}
