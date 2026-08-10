package sh.zolt.jarproof.engine;

/**
 * One field or method named by executable bytecode, with the shape the JVM will check it against.
 *
 * <p>{@code ownerInternalName} is the class named by the reference, not the class that ends up
 * declaring the member: resolution walks the hierarchy from there. A {@link ReferenceKind#METHOD_HANDLE}
 * reference may name either a field or a method, which its descriptor settles — a method descriptor
 * opens with a parenthesis. {@code referencingMethod} is the declaring method's name joined to its
 * descriptor, which identifies the call site within its class.
 */
record MemberReference(
        ReferenceKind kind,
        String ownerInternalName,
        String name,
        String descriptor,
        String referencingMethod) {
}
