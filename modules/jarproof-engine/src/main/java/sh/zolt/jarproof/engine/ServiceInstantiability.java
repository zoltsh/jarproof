package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

/**
 * The criteria a provider must meet before a classpath {@code ServiceLoader} can construct it.
 *
 * <p>On a classpath there is exactly one way in: a public class with a public no-argument
 * constructor. The static {@code provider()} factory is a module-path form and is deliberately not
 * accepted here, so a classpath provider that relies on it still fails this check — which is what
 * the runtime does to it too.
 *
 * <p>Every criterion a provider fails is reported, so one reading of the diagnostic is enough to fix
 * the class. An interface is the single exception: it is reported only as an interface, because the
 * class file format already requires every interface to be abstract and to declare no constructor,
 * so restating those tells the reader nothing they can act on.
 */
final class ServiceInstantiability {
    private static final String INTERFACE = "is an interface rather than a class";
    private static final String ABSTRACT = "is abstract";
    private static final String NOT_PUBLIC = "is not public";
    private static final String WITHOUT_CONSTRUCTOR = "declares no public no-argument constructor";
    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final String NO_ARGUMENT_DESCRIPTOR = "()V";

    private ServiceInstantiability() {
    }

    /**
     * Returns why this provider cannot be constructed.
     *
     * @param shape the declared shape of the provider class
     * @return the failed criteria in reading order, or empty when the provider can be constructed
     */
    static List<String> failures(ClassShape shape) {
        if (isSet(shape, Opcodes.ACC_INTERFACE)) {
            return List.of(INTERFACE);
        }
        List<String> failures = new ArrayList<>();
        if (isSet(shape, Opcodes.ACC_ABSTRACT)) {
            failures.add(ABSTRACT);
        }
        if (!isSet(shape, Opcodes.ACC_PUBLIC)) {
            failures.add(NOT_PUBLIC);
        }
        if (!hasPublicNoArgumentConstructor(shape)) {
            failures.add(WITHOUT_CONSTRUCTOR);
        }
        return List.copyOf(failures);
    }

    private static boolean hasPublicNoArgumentConstructor(ClassShape shape) {
        return shape.members().stream().anyMatch(ServiceInstantiability::isPublicNoArgumentConstructor);
    }

    private static boolean isPublicNoArgumentConstructor(MemberShape member) {
        return member.name().equals(CONSTRUCTOR_NAME)
                && member.descriptor().equals(NO_ARGUMENT_DESCRIPTOR)
                && (member.accessFlags() & Opcodes.ACC_PUBLIC) != 0;
    }

    private static boolean isSet(ClassShape shape, int flag) {
        return (shape.accessFlags() & flag) != 0;
    }
}
