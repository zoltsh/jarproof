package sh.zolt.jarproof.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;

/** Builds service configuration files, provider class files, and the catalog the service checks read. */
final class ServiceFixture {
    static final String SERVICE = "com.acme.codec.Codec";
    static final String SERVICE_INTERNAL = "com/acme/codec/Codec";
    static final String SERVICE_ENTRY = ServiceDeclaration.RESOURCE_PREFIX + SERVICE;
    static final String PROVIDER = "com.acme.codec.PlainCodec";
    static final String PROVIDER_INTERNAL = "com/acme/codec/PlainCodec";
    static final String ABSENT_PROVIDER = "com.acme.codec.GoneCodec";
    static final String ABSENT_PROVIDER_INTERNAL = "com/acme/codec/GoneCodec";
    static final int CONCRETE = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
    private static final String CONSTRUCTOR = "<init>";
    private static final String NO_ARGUMENT = "()V";
    private static final JdkSymbolCatalog PLATFORM = JdkSymbolCatalog.forRelease(17);

    private ServiceFixture() {
    }

    static ArtifactCatalog catalog(List<Path> applications, List<Path> classpath) {
        return ArtifactCatalog.read(EngineFixture.request(applications, classpath, 17), new ResourceBudget());
    }

    static List<Finding> findings(List<Path> applications, List<Path> classpath) {
        return ServiceProviderCheck.run(catalog(applications, classpath), PLATFORM);
    }

    static List<Finding> findings(Path artifact) {
        return findings(List.of(artifact), List.of());
    }

    static List<Finding> findings(Path artifact, ResourceBudget budget) {
        return ServiceProviderCheck.run(catalog(List.of(artifact), List.of()), PLATFORM, budget);
    }

    /** Adds one service configuration file, named after the service, to a set of archive entries. */
    static Map<String, byte[]> serviceFile(Map<String, byte[]> entries, String serviceName, String content) {
        return serviceFile(entries, serviceName, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Adds one service configuration file whose bytes are chosen by the caller. */
    static Map<String, byte[]> serviceFile(Map<String, byte[]> entries, String serviceName, byte[] content) {
        entries.put(ServiceDeclaration.RESOURCE_PREFIX + serviceName, content);
        return entries;
    }

    /** Joins provider lines into file content, newline separated and newline terminated. */
    static String lines(String... provider) {
        return String.join("\n", provider) + "\n";
    }

    /** Encodes configuration text the way a well-formed resource stores it. */
    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A public interface, which is the ordinary shape of a service type. */
    static byte[] serviceInterface(String internalName) {
        return type(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, internalName, List.of());
    }

    /** A provider a classpath ServiceLoader can construct. */
    static byte[] provider(String internalName, String... interfaces) {
        return constructed(CONCRETE, internalName, EngineFixture.OBJECT, List.of(interfaces), Opcodes.ACC_PUBLIC);
    }

    /** A provider that inherits everything, including the service type, from its superclass. */
    static byte[] derivedProvider(String internalName, String superName) {
        return constructed(CONCRETE, internalName, superName, List.of(), Opcodes.ACC_PUBLIC);
    }

    /** An abstract provider: constructible in principle, never instantiable. */
    static byte[] abstractProvider(String internalName, String... interfaces) {
        return constructed(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, internalName, EngineFixture.OBJECT,
                List.of(interfaces), Opcodes.ACC_PUBLIC);
    }

    /** A provider whose only constructor is private. */
    static byte[] hiddenConstructorProvider(String internalName, String... interfaces) {
        return constructed(CONCRETE, internalName, EngineFixture.OBJECT, List.of(interfaces), Opcodes.ACC_PRIVATE);
    }

    /** A package-private provider that declares a field and whose only constructor needs an argument. */
    static byte[] packagePrivateProvider(String internalName, String... interfaces) {
        ClassWriter writer = header(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, internalName, EngineFixture.OBJECT,
                List.of(interfaces));
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "name", "Ljava/lang/String;", null, null)
                .visitEnd();
        constructor(writer, Opcodes.ACC_PUBLIC, "(Ljava/lang/String;)V", EngineFixture.OBJECT);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] type(int access, String internalName, List<String> interfaces) {
        ClassWriter writer = header(access, internalName, EngineFixture.OBJECT, interfaces);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] constructed(
            int access, String internalName, String superName, List<String> interfaces, int constructorAccess) {
        ClassWriter writer = header(access, internalName, superName, interfaces);
        constructor(writer, constructorAccess, NO_ARGUMENT, superName);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter header(int access, String internalName, String superName, List<String> interfaces) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, access, internalName, null, superName, interfaces.toArray(String[]::new));
        return writer;
    }

    private static void constructor(ClassWriter writer, int access, String descriptor, String superName) {
        MethodVisitor method = writer.visitMethod(access, CONSTRUCTOR, descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, CONSTRUCTOR, NO_ARGUMENT, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
    }
}
