package sh.zolt.jarproof.engine;

import java.util.List;
import java.util.Optional;
import org.objectweb.asm.Opcodes;

/**
 * One class name after resolution: the name that was asked for, the shape found for it, and where it
 * was found.
 *
 * <p>{@code internalName} is the name the reference used rather than the identity the class file
 * claims for itself. The two normally agree, and when they disagree the runtime still finds the class
 * by the name it was stored under, so that is the name resolution has to keep.
 *
 * <p>Exactly one of {@code declaration} and {@code platformModule} is present. A declaration means an
 * artifact on the effective classpath supplied the class; a module name means the target runtime did.
 * The distinction is not cosmetic: it decides how a report names the source, and it decides how
 * accessibility is judged, because the bundled runtime profile describes exported API only.
 */
record ResolvedClass(
        String internalName,
        ClassShape shape,
        Optional<ClassDeclaration> declaration,
        Optional<String> platformModule) {
    /** Builds the resolved form of a class an artifact on the classpath declares. */
    static ResolvedClass ofClasspath(String internalName, ClassShape shape, ClassDeclaration declaration) {
        return new ResolvedClass(internalName, shape, Optional.of(declaration), Optional.empty());
    }

    /** Builds the resolved form of a class the target runtime supplies. */
    static ResolvedClass ofPlatform(String internalName, ClassShape shape, String module) {
        return new ResolvedClass(internalName, shape, Optional.empty(), Optional.of(module));
    }

    /** Returns whether this is an interface, which decides which JVMS resolution rule applies. */
    boolean isInterface() {
        return flagged(Opcodes.ACC_INTERFACE);
    }

    /** Returns whether this class is public, which is the whole of the class-level access question. */
    boolean isPublic() {
        return flagged(Opcodes.ACC_PUBLIC);
    }

    /** Returns whether the target runtime supplied this class rather than the classpath. */
    boolean fromPlatform() {
        return declaration.isEmpty();
    }

    /** Returns the run-time package this class belongs to, empty text for the unnamed package. */
    String packageName() {
        return ArchiveLayout.packageOf(internalName).orElse("");
    }

    /**
     * Returns the member this class itself declares under a name and descriptor.
     *
     * @param name member name
     * @param descriptor field or method descriptor
     * @return the declared member, or empty when this class declares no such member
     */
    Optional<MemberShape> declared(String name, String descriptor) {
        return shape.members().stream()
                .filter(member -> member.name().equals(name) && member.descriptor().equals(descriptor))
                .findFirst();
    }

    /** Returns the members this class declares under a name, whatever their descriptors. */
    List<MemberShape> named(String name) {
        return shape.members().stream().filter(member -> member.name().equals(name)).toList();
    }

    private boolean flagged(int mask) {
        return (shape.accessFlags() & mask) != 0;
    }
}
