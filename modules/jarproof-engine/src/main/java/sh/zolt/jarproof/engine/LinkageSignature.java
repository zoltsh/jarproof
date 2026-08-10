package sh.zolt.jarproof.engine;

import java.util.Set;
import org.objectweb.asm.Opcodes;

/**
 * Recognises a signature-polymorphic method, per JVMS §2.9.3.
 *
 * <p>{@code MethodHandle.invoke}, {@code MethodHandle.invokeExact} and the {@code VarHandle} accessors
 * are declared once, taking {@code Object[]}, and the JVM links a call to them against whatever
 * descriptor the call site wrote. Nothing else in the class file format behaves this way. Checking such
 * a call against the declared descriptor would report a missing method at every single use, which is
 * why this is special-cased rather than tuned.
 *
 * <p>A declaration qualifies when it lives in one of the two runtime types, takes exactly one
 * {@code Object[]} parameter, and is both variable-arity and native. The specification constrains the
 * parameter list only, not the return type: {@code MethodHandle.invoke} returns {@code Object} while
 * {@code VarHandle.compareAndSet} returns {@code boolean} and {@code VarHandle.set} returns nothing, so
 * matching on the parameter prefix is what keeps all of them quiet.
 */
final class LinkageSignature {
    private static final Set<String> POLYMORPHIC_OWNERS =
            Set.of("java/lang/invoke/MethodHandle", "java/lang/invoke/VarHandle");
    private static final String POLYMORPHIC_PARAMETERS = "([Ljava/lang/Object;)";
    private static final int POLYMORPHIC_FLAGS = Opcodes.ACC_VARARGS | Opcodes.ACC_NATIVE;

    private LinkageSignature() {
    }

    /**
     * Returns whether a reference names a signature-polymorphic method, so any descriptor links.
     *
     * @param owner the class the reference resolved to
     * @param name the referenced member name
     * @return whether the reference matches whatever descriptor it wrote
     */
    static boolean polymorphic(ResolvedClass owner, String name) {
        if (!POLYMORPHIC_OWNERS.contains(owner.internalName())) {
            return false;
        }
        return owner.named(name).stream().anyMatch(LinkageSignature::declaredPolymorphic);
    }

    private static boolean declaredPolymorphic(MemberShape member) {
        return member.descriptor().startsWith(POLYMORPHIC_PARAMETERS)
                && (member.accessFlags() & POLYMORPHIC_FLAGS) == POLYMORPHIC_FLAGS;
    }
}
