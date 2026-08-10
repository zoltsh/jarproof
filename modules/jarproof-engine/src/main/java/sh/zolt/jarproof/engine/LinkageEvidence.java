package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Scope;

/**
 * The facts a linkage finding shows, and the canonical way to spell a member.
 *
 * <p>Every line here is something a reader can go and check: which artifact supplied the class that
 * resolved, which method inside the referencing class named it, and — when a method is missing — what
 * the resolved class does declare under that name. That last line is the difference between a report
 * that says a method is gone and a report that shows the descriptor drifted by one parameter.
 *
 * <p>Nothing here consults the file system or the clock, and no artifact path is rewritten, so the same
 * inputs describe themselves identically on every machine.
 */
final class LinkageEvidence {
    private static final int MAXIMUM_CANDIDATES = 3;
    private static final char MEMBER_SEPARATOR = '#';
    private static final String PROVING_CHAIN = "reachable via: ";

    private LinkageEvidence() {
    }

    /**
     * Spells one member the canonical way: the class that owns it, a separator, then its name joined
     * to its descriptor.
     *
     * <p>This is the one place the repository writes that shape. A baseline fingerprints it, a call
     * graph node reads back as one, and a reconstructed chain is a list of them, so all three agree by
     * construction instead of by convention.
     *
     * @param owner the internal name of the class that owns the member
     * @param signature the member's name joined to its descriptor
     * @return the canonical spelling
     */
    static String member(String owner, String signature) {
        return owner + MEMBER_SEPARATOR + signature;
    }

    /**
     * Spells a member reference as the stable symbol a baseline can fingerprint.
     *
     * @param reference the reference as the bytecode wrote it
     * @return the class the reference named, a separator, then the member name and descriptor
     */
    static String subject(MemberReference reference) {
        return member(reference.ownerInternalName(), reference.name() + reference.descriptor());
    }

    /**
     * Evidence that the referencing method really executes, which only {@link Scope#REACHABLE} can show.
     *
     * <p>Every other line of a linkage finding explains what fails to resolve. This one answers the
     * question a reader asks first at this scope — why is the broken method reached at all — with the
     * path {@link Reachability} proved, running from the entry surface to the referencing method itself.
     *
     * @param chain the reconstructed path, already rendered
     * @return the one line that proves the finding is on an executable path
     */
    static Evidence reachableVia(String chain) {
        return new Evidence(PROVING_CHAIN + chain);
    }

    /** Evidence that a name is declared nowhere at all. */
    static List<Evidence> absent(String internalName, String referencingMethod) {
        return List.of(
                new Evidence("no artifact on the effective classpath and no module of the target runtime"
                        + " declares " + internalName),
                callSite(referencingMethod));
    }

    /** Evidence naming where the class resolved and which call site reached for it. */
    static List<Evidence> resolvedOwner(ResolvedClass owner, String referencingMethod) {
        return List.of(source(owner), callSite(referencingMethod));
    }

    /** Evidence for a reference whose form disagrees with the type it resolved to. */
    static List<Evidence> kind(ResolvedClass owner, String referencingMethod) {
        return List.of(
                source(owner),
                new Evidence("the resolved type is " + (owner.isInterface() ? "an interface" : "a class")),
                callSite(referencingMethod));
    }

    /** Evidence for a class the referencing class is not allowed to see. */
    static List<Evidence> classAccess(ResolvedClass owner, ResolvedClass referencing, String referencingMethod) {
        return List.of(
                source(owner),
                new Evidence("it is not public, and " + referencing.internalName()
                        + " is outside its run-time package"),
                callSite(referencingMethod));
    }

    /** Evidence for a member whose static form contradicts the reference. */
    static List<Evidence> memberForm(ResolvedClass owner, ResolvedMember resolved, String referencingMethod) {
        return List.of(
                source(owner),
                trait(resolved, resolved.isStatic() ? "static" : "an instance member"),
                callSite(referencingMethod));
    }

    /** Evidence for a member the referencing class is not allowed to see. */
    static List<Evidence> memberAccess(ResolvedClass owner, ResolvedMember resolved, String referencingMethod) {
        return List.of(source(owner), trait(resolved, visibility(resolved)), callSite(referencingMethod));
    }

    /**
     * Evidence for a missing method, ending with what the resolved class does declare under that name.
     *
     * @param owner the class the reference named
     * @param reference the method reference that found nothing
     * @return the resolution facts followed by up to three same-name declarations
     */
    static List<Evidence> candidates(ResolvedClass owner, MemberReference reference) {
        List<Evidence> evidence = new ArrayList<>(resolvedOwner(owner, reference.referencingMethod()));
        owner.named(reference.name()).stream()
                .filter(member -> !member.descriptor().equals(reference.descriptor()))
                .limit(MAXIMUM_CANDIDATES)
                .forEach(member -> evidence.add(new Evidence(
                        "the resolved class also declares " + member.name() + member.descriptor())));
        return List.copyOf(evidence);
    }

    private static Evidence source(ResolvedClass resolved) {
        return resolved.declaration()
                .map(claim -> new Evidence(
                        "selected " + claim.artifactPath() + " (" + claim.declared().shortDigest() + ")"))
                .orElseGet(() -> new Evidence("supplied by the target runtime from "
                        + resolved.platformModule().orElse(resolved.internalName())));
    }

    private static Evidence callSite(String referencingMethod) {
        return new Evidence("referenced from " + referencingMethod);
    }

    private static Evidence trait(ResolvedMember resolved, String trait) {
        return new Evidence("the declaration in " + resolved.owner().internalName() + " is " + trait);
    }

    private static String visibility(ResolvedMember resolved) {
        if (resolved.isPrivate()) {
            return "private";
        }
        return resolved.isProtected() ? "protected" : "package-private";
    }
}
