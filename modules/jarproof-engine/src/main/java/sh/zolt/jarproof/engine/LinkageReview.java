package sh.zolt.jarproof.engine;

import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

/**
 * Everything needed to judge the references of one class, and the findings that judgement produces.
 *
 * <p>A finding is always located at the <em>referencing</em> class rather than at the symbol that is
 * missing. That is where the reader has to go to fix it, and it is what makes the report actionable
 * when a single absent artifact breaks two hundred call sites in one place.
 *
 * <p>The location this holds names the referencing class file and the source file it was compiled
 * from; each finding narrows it to the line of the reference that produced it, so two breaks in one
 * class are told apart by where they are written. Line 0 does not exist, so an absent line is the
 * honest answer for a class compiled without a line table and for a reference the table does not
 * cover. Linkage findings are the only family that carries source coordinates in this pass: the
 * classpath, runtime, and service families are located at an artifact or at a whole class, where a
 * line would be an invention rather than a fact.
 *
 * <p>Only the references the bytecode of this class triggers are reviewed. The declared superclass and
 * interfaces are not among them by design: the reference index excludes generic signatures, annotations,
 * declared exceptions, and record metadata, none of which the JVM resolves on the path that raises a
 * linkage error.
 */
record LinkageReview(
        ResolutionTable table,
        ResolvedClass referencing,
        ArtifactLocation location,
        Severity severity) {
    /**
     * Reviews one class named by executable bytecode.
     *
     * @param reference the class a type instruction, catch clause, or class constant named
     * @return the finding it produces, or empty when the reference links
     */
    Optional<Finding> type(TypeReference reference) {
        Optional<ResolvedClass> resolved = table.find(reference.internalName());
        if (resolved.isEmpty()) {
            return missingClass(reference.internalName(), reference.referencingMethod(), reference.line());
        }
        if (AccessRules.classAccessible(resolved.get(), referencing)) {
            return Optional.empty();
        }
        return Optional.of(inaccessibleClass(resolved.get(), reference.referencingMethod(), reference.line()));
    }

    /**
     * Reviews one field or method named by executable bytecode.
     *
     * @param reference the member reference to resolve
     * @return the finding it produces, or empty when the reference links
     */
    Optional<Finding> member(MemberReference reference) {
        return LinkageMemberReview.of(this, reference);
    }

    /**
     * Reports a class name nothing declares.
     *
     * <p>Stays quiet when the classpath does claim the name and its bytes were unreadable: that entry
     * already carries its own diagnostic, and calling the class missing on top of it would be false.
     *
     * @param internalName the name that resolved to nothing
     * @param referencingMethod the call site that named it
     * @param line the source line the call site sits on, when the class file records one
     * @return the finding, or empty when an unreadable entry claims the name
     */
    Optional<Finding> missingClass(String internalName, String referencingMethod, Optional<Integer> line) {
        if (table.claimedWithoutShape(internalName)) {
            return Optional.empty();
        }
        return Optional.of(fault(
                LinkageFault.MISSING_CLASS,
                internalName,
                LinkageEvidence.absent(internalName, referencingMethod),
                line));
    }

    /** Reports a class that resolved and cannot be seen from here. */
    Finding inaccessibleClass(ResolvedClass owner, String referencingMethod, Optional<Integer> line) {
        return fault(
                LinkageFault.INACCESSIBLE_CLASS,
                owner.internalName(),
                LinkageEvidence.classAccess(owner, referencing, referencingMethod),
                line);
    }

    /** Reports a reference whose form contradicts whether the resolved type is an interface. */
    Finding kindMismatch(ResolvedClass owner, MemberReference reference) {
        return fault(
                LinkageFault.KIND_MISMATCH,
                LinkageEvidence.subject(reference),
                LinkageEvidence.kind(owner, reference.referencingMethod()),
                reference.line());
    }

    /** Reports a member that resolution did not find, as a field or a method to match the reference. */
    Finding missingMember(ResolvedClass owner, MemberReference reference) {
        if (ResolutionRules.namesMethod(reference.descriptor())) {
            return fault(
                    LinkageFault.MISSING_METHOD,
                    LinkageEvidence.subject(reference),
                    LinkageEvidence.candidates(owner, reference),
                    reference.line());
        }
        return fault(
                LinkageFault.MISSING_FIELD,
                LinkageEvidence.subject(reference),
                LinkageEvidence.resolvedOwner(owner, reference.referencingMethod()),
                reference.line());
    }

    /** Reports a member whose static form contradicts the reference that reached it. */
    Finding staticMismatch(ResolvedClass owner, MemberReference reference, ResolvedMember resolved) {
        return fault(
                LinkageFault.STATIC_MISMATCH,
                LinkageEvidence.subject(reference),
                LinkageEvidence.memberForm(owner, resolved, reference.referencingMethod()),
                reference.line());
    }

    /** Reports a member that resolved and cannot be seen from here. */
    Finding inaccessibleMember(ResolvedClass owner, MemberReference reference, ResolvedMember resolved) {
        return fault(
                LinkageFault.INACCESSIBLE_MEMBER,
                LinkageEvidence.subject(reference),
                LinkageEvidence.memberAccess(owner, resolved, reference.referencingMethod()),
                reference.line());
    }

    private Finding fault(LinkageFault fault, String subject, List<Evidence> evidence, Optional<Integer> line) {
        return fault.at(at(line), severity, subject, evidence);
    }

    /** The referencing class file, narrowed to the line this reference is written on. */
    private ArtifactLocation at(Optional<Integer> line) {
        return new ArtifactLocation(location.artifact(), location.classEntry(), location.sourceFile(), line);
    }
}
