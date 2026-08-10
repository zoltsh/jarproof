package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * One field or method named by executable bytecode, with the shape the JVM will check it against.
 *
 * <p>{@code ownerInternalName} is the class named by the reference, not the class that ends up
 * declaring the member: resolution walks the hierarchy from there. A {@link ReferenceKind#METHOD_HANDLE}
 * reference may name either a field or a method, which its descriptor settles — a method descriptor
 * opens with a parenthesis. {@code referencingMethod} is the declaring method's name joined to its
 * descriptor, which identifies the call site within its class.
 *
 * <p>{@code line} is the source line the naming instruction belongs to, and it is absent whenever
 * the class file cannot answer: compiled without a {@code LineNumberTable}, or named by a
 * constant-pool entry the bytecode reaches outside any line the table covers.
 */
record MemberReference(
        ReferenceKind kind,
        String ownerInternalName,
        String name,
        String descriptor,
        String referencingMethod,
        Optional<Integer> line) {
}
