package sh.zolt.jarproof.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;

/** Builds module descriptors, reserved module names, and provider classes for the module checks. */
final class ModuleFixture {
    static final String MODULE = "com.acme.orders";
    static final String DESCRIPTOR_ENTRY = ModuleClaimReader.DESCRIPTOR_NAME + ArchiveLayout.CLASS_SUFFIX;
    static final String PACKAGE = "com/acme/orders";
    static final String ABSENT_MODULE = "com.acme.absent";
    static final String PLATFORM_MODULE = "java.sql";
    static final String PLATFORM_SERVICE = "java/sql/Driver";
    static final String FACTORY = "provider";
    private static final String AUTOMATIC_NAME = "Automatic-Module-Name";
    private static final String CONSTRUCTOR = "<init>";
    private static final String NO_ARGUMENT = "()V";
    private static final String VOID_RETURN = ")V";
    private static final JdkSymbolCatalog PLATFORM = JdkSymbolCatalog.forRelease(17);

    private ModuleFixture() {
    }

    /** Reads the classpath the module checks run over, so a test can disturb it afterwards. */
    static ArtifactCatalog catalog(List<Path> applications, List<Path> classpath) {
        return ArtifactCatalog.read(
                EngineFixture.request(applications, classpath, 17), new ResourceBudget());
    }

    /** Runs the module checks over an already read classpath. */
    static List<Finding> findings(ArtifactCatalog catalog) {
        return ModuleCheck.run(catalog, PLATFORM);
    }

    /** Runs the module checks over a classpath of application roots and plain entries. */
    static List<Finding> findings(List<Path> applications, List<Path> classpath) {
        return findings(catalog(applications, classpath));
    }

    /** Runs the module checks over one artifact. */
    static List<Finding> findings(Path artifact) {
        return findings(List.of(artifact), List.of());
    }

    /**
     * Writes one module descriptor. Every descriptor requires {@code java.base} the way a compiler
     * writes it, so the implicit requirement is exercised by every test rather than assumed.
     */
    static byte[] descriptor(String moduleName, Consumer<ModuleVisitor> clauses) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_MODULE, ModuleClaimReader.DESCRIPTOR_NAME, null, null, null);
        ModuleVisitor module = writer.visitModule(moduleName, 0, null);
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        clauses.accept(module);
        module.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A descriptor that declares nothing beyond its own name. */
    static byte[] bareDescriptor(String moduleName) {
        return descriptor(moduleName, module -> {
        });
    }

    /** Adds a descriptor to a set of archive entries under the entry name the runtime reads. */
    static Map<String, byte[]> withDescriptor(Map<String, byte[]> entries, byte[] descriptor) {
        entries.put(DESCRIPTOR_ENTRY, descriptor);
        return entries;
    }

    /** A manifest reserving one module name for the module path. */
    static Manifest reserving(String moduleName) {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(new Attributes.Name(AUTOMATIC_NAME), moduleName);
        return manifest;
    }

    /** A class file that is named like a descriptor but declares no module. */
    static byte[] impostorDescriptor() {
        return EngineFixture.classFile(ModuleClaimReader.DESCRIPTOR_NAME);
    }

    /** A descriptor whose class file version no parser here accepts. */
    static byte[] futureDescriptor(String moduleName) {
        return EngineFixture.withVersion(bareDescriptor(moduleName), 200, 0);
    }

    /** A provider whose factory method has the signature the caller chooses. */
    static byte[] shapedFactoryProvider(String internalName, String service, String methodDescriptor) {
        ClassWriter writer = header(internalName, service);
        constructor(writer, Opcodes.ACC_PRIVATE);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, FACTORY, methodDescriptor, null, null);
        method.visitCode();
        if (methodDescriptor.endsWith(VOID_RETURN)) {
            method.visitInsn(Opcodes.RETURN);
        } else {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ARETURN);
        }
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A provider whose only entry point is a factory method the caller shapes. */
    static byte[] factoryProvider(String internalName, String service, int access, String returned) {
        ClassWriter writer = header(internalName, service);
        constructor(writer, Opcodes.ACC_PRIVATE);
        factory(writer, access, returned);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A provider with a public constructor that also declares a factory method returning something else. */
    static byte[] constructedFactoryProvider(String internalName, String service, String returned) {
        ClassWriter writer = header(internalName, service);
        constructor(writer, Opcodes.ACC_PUBLIC);
        factory(writer, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, returned);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A provider whose factory method is an instance method rather than a static one. */
    static byte[] instanceFactoryProvider(String internalName, String service) {
        ClassWriter writer = header(internalName, service);
        constructor(writer, Opcodes.ACC_PRIVATE);
        factory(writer, Opcodes.ACC_PUBLIC, service);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A provider that declares a field named like a factory method, which is not one. */
    static byte[] factoryFieldProvider(String internalName, String service) {
        ClassWriter writer = header(internalName, service);
        constructor(writer, Opcodes.ACC_PRIVATE);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, FACTORY, descriptorOf(service), null, null)
                .visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter header(String internalName, String service) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                internalName,
                null,
                EngineFixture.OBJECT,
                new String[] {service});
        return writer;
    }

    private static void factory(ClassWriter writer, int access, String returned) {
        MethodVisitor method = writer.visitMethod(access, FACTORY, "()" + descriptorOf(returned), null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static void constructor(ClassWriter writer, int access) {
        MethodVisitor method = writer.visitMethod(access, CONSTRUCTOR, NO_ARGUMENT, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, EngineFixture.OBJECT, CONSTRUCTOR, NO_ARGUMENT, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static String descriptorOf(String internalName) {
        return "L" + internalName + ";";
    }
}
