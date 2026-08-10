package sh.zolt.jarproof.engine;

import java.util.Optional;
import sh.zolt.jarproof.api.Finding;

/**
 * Resolves one member reference in the order the JVM does, and reports the first step that fails.
 *
 * <p>The order is the specification's, and it is load-bearing. The named class has to resolve before
 * anything can be looked up inside it; accessibility of that class is decided during its resolution;
 * the class-or-interface form of the reference decides which lookup procedure runs before the lookup
 * starts; and the member's own shape and accessibility are checked only once it has been found. One
 * reference therefore yields at most one finding — the earliest failure, which is the one the JVM would
 * actually raise.
 *
 * <p>Two reference shapes are treated specially. A member named through a constant-pool method handle
 * is checked for existence and accessibility only: the handle's kind is not retained by the reference
 * index, so whether it reads a static or an instance member cannot be known, and guessing would
 * manufacture a static-mismatch finding out of nothing. And a member named on an array type is skipped
 * entirely, because an array's members are the JVM's own — its length, and a public {@code clone} that
 * the array type synthesises rather than inherits.
 */
final class LinkageMemberReview {
    private static final char ARRAY_DESCRIPTOR_START = '[';

    private LinkageMemberReview() {
    }

    /**
     * Reviews one member reference.
     *
     * @param review the referencing class and the table to resolve against
     * @param reference the field or method reference as the bytecode wrote it
     * @return the finding it produces, or empty when the reference links
     */
    static Optional<Finding> of(LinkageReview review, MemberReference reference) {
        if (reference.ownerInternalName().charAt(0) == ARRAY_DESCRIPTOR_START) {
            return Optional.empty();
        }
        Optional<ResolvedClass> owner = review.table().find(reference.ownerInternalName());
        if (owner.isEmpty()) {
            return review.missingClass(
                    reference.ownerInternalName(), reference.referencingMethod(), reference.line());
        }
        return withinOwner(review, reference, owner.get());
    }

    private static Optional<Finding> withinOwner(
            LinkageReview review, MemberReference reference, ResolvedClass owner) {
        if (LinkageSignature.polymorphic(owner, reference.name())) {
            return Optional.empty();
        }
        if (!AccessRules.classAccessible(owner, review.referencing())) {
            return Optional.of(
                    review.inaccessibleClass(owner, reference.referencingMethod(), reference.line()));
        }
        if (contradictsKind(reference.kind(), owner)) {
            return Optional.of(review.kindMismatch(owner, reference));
        }
        return withinMember(review, reference, owner);
    }

    private static Optional<Finding> withinMember(
            LinkageReview review, MemberReference reference, ResolvedClass owner) {
        Optional<ResolvedMember> resolved = lookUp(review.table(), reference, owner);
        if (resolved.isEmpty()) {
            return Optional.of(review.missingMember(owner, reference));
        }
        return contradiction(review, reference, owner, resolved.get());
    }

    private static Optional<Finding> contradiction(
            LinkageReview review, MemberReference reference, ResolvedClass owner, ResolvedMember resolved) {
        if (checksReceiver(reference.kind()) && resolved.isStatic() != expectsStatic(reference.kind())) {
            return Optional.of(review.staticMismatch(owner, reference, resolved));
        }
        if (!AccessRules.memberAccessible(review.table(), resolved, review.referencing())) {
            return Optional.of(review.inaccessibleMember(owner, reference, resolved));
        }
        return Optional.empty();
    }

    private static Optional<ResolvedMember> lookUp(
            ResolutionTable table, MemberReference reference, ResolvedClass owner) {
        if (ResolutionRules.namesMethod(reference.descriptor()) && owner.isInterface()) {
            return ResolutionRules.throughInterface(table, owner, reference);
        }
        return ResolutionRules.throughHierarchy(table, owner, reference);
    }

    /**
     * Returns whether the reference form and the resolved type disagree, per the JVMS §5.4.3.3 and
     * §5.4.3.4 split.
     *
     * <p>An interface invocation resolves under §5.4.3.4, which refuses a class outright. A virtual
     * invocation resolves under §5.4.3.3, which refuses an interface outright. Nothing else disagrees:
     * a static interface method is legal bytecode, an interface superclass call is how a default method
     * is reached, and an interface may declare the static constants a field reference reads.
     *
     * @param kind how the bytecode reaches the member
     * @param owner the class or interface the reference resolved to
     * @return whether resolution fails on the form of the reference alone
     */
    private static boolean contradictsKind(ReferenceKind kind, ResolvedClass owner) {
        if (kind == ReferenceKind.INVOKE_INTERFACE) {
            return !owner.isInterface();
        }
        return kind == ReferenceKind.INVOKE_VIRTUAL && owner.isInterface();
    }

    private static boolean checksReceiver(ReferenceKind kind) {
        return kind != ReferenceKind.METHOD_HANDLE;
    }

    private static boolean expectsStatic(ReferenceKind kind) {
        return kind == ReferenceKind.GET_STATIC
                || kind == ReferenceKind.PUT_STATIC
                || kind == ReferenceKind.INVOKE_STATIC;
    }
}
