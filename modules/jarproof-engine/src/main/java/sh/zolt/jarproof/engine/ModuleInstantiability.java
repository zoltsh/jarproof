package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * The criteria a provider must meet before the module system can construct it.
 *
 * <p>A module path offers one way in that a classpath does not: a public static {@code provider}
 * method taking no arguments, whose returned type carries the service. When a class declares one, its
 * constructors stop mattering entirely. DESIGN.md deferred exactly this rule to the JP5xxx range, and
 * the classpath rule stays as it was — {@link ServiceInstantiability} still accepts nothing but a
 * public no-argument constructor, because that is all a classpath {@code ServiceLoader} looks for.
 *
 * <p>Everything else the two worlds agree on, so the shared criterion is asked of the classpath rule
 * rather than restated here. The class's own members are presented to it under plain public access
 * flags, which leaves the interface, abstract, and visibility criteria unable to fail, so an empty
 * answer means precisely that a public no-argument constructor is declared. The JVM's own name for a
 * constructor therefore stays spelled in one place in the engine instead of two.
 *
 * <p>The returned type of a factory is measured the way every other assignability question in the
 * engine is: it fails only when the walk proves it is not a subtype. A returned type nothing on this
 * classpath declares leaves the question unanswerable, and an unanswerable question is not a finding.
 */
final class ModuleInstantiability {
    private static final String PROVIDER_METHOD = "provider";
    private static final String INTERFACE_CRITERION = "is an interface, and only a class can be constructed";
    private static final String ABSTRACT_CRITERION = "is declared abstract";
    private static final String VISIBILITY_CRITERION = "is not declared public";
    private static final String ENTRY_POINT_CRITERION =
            "declares neither a public no-argument constructor nor a public static provider method";
    private static final int FACTORY_FLAGS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

    private final ServiceTypeHierarchy hierarchy;

    ModuleInstantiability(ServiceTypeHierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    /**
     * Returns why the module system cannot construct this provider.
     *
     * @param shape the declared shape of the provider class
     * @param serviceInternalName internal name of the service the provider is registered under, which
     *     a factory method's returned type has to carry
     * @return the failed criteria in reading order, or empty when the provider can be constructed
     */
    List<String> failures(ClassShape shape, String serviceInternalName) {
        if (isSet(shape, Opcodes.ACC_INTERFACE)) {
            return List.of(INTERFACE_CRITERION);
        }
        List<String> failures = new ArrayList<>();
        if (isSet(shape, Opcodes.ACC_ABSTRACT)) {
            failures.add(ABSTRACT_CRITERION);
        }
        if (!isSet(shape, Opcodes.ACC_PUBLIC)) {
            failures.add(VISIBILITY_CRITERION);
        }
        if (!hasConstructor(shape) && !hasFactory(shape, serviceInternalName)) {
            failures.add(ENTRY_POINT_CRITERION);
        }
        return List.copyOf(failures);
    }

    private static boolean hasConstructor(ClassShape shape) {
        ClassShape constructible = new ClassShape(
                shape.internalName(),
                Opcodes.ACC_PUBLIC,
                shape.superInternalName(),
                shape.interfaceInternalNames(),
                shape.nestHostInternalName(),
                shape.nestMemberInternalNames(),
                shape.members());
        return ServiceInstantiability.failures(constructible).isEmpty();
    }

    private boolean hasFactory(ClassShape shape, String serviceInternalName) {
        return shape.members().stream().anyMatch(member -> isFactory(member, serviceInternalName));
    }

    private boolean isFactory(MemberShape member, String serviceInternalName) {
        Type declared = Type.getType(member.descriptor());
        return member.name().equals(PROVIDER_METHOD)
                && (member.accessFlags() & FACTORY_FLAGS) == FACTORY_FLAGS
                && declared.getSort() == Type.METHOD
                && declared.getArgumentTypes().length == 0
                && carries(declared.getReturnType(), serviceInternalName);
    }

    private boolean carries(Type returned, String serviceInternalName) {
        return returned.getSort() == Type.OBJECT
                && !hierarchy.provablyLacks(returned.getInternalName(), serviceInternalName);
    }

    private static boolean isSet(ClassShape shape, int flag) {
        return (shape.accessFlags() & flag) != 0;
    }
}
