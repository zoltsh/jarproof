package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * The seven ways one resolved reference can fail to link, and what each one means to a reader.
 *
 * <p>Each constant owns its diagnostic code, its one-line summary, the paragraph that explains how the
 * JVM arrives there, and the single step that fixes it. The runtime failure each one predicts is the
 * mapping DESIGN §5 publishes, restated here as the only place the engine decides it.
 *
 * <p>{@code AbstractMethodError} is deliberately absent. An invocation that resolves to an abstract
 * interface method is ordinary, and proving that a concrete receiver lacks an implementation needs
 * evidence this analysis does not have, so that code stays reserved rather than guessed at.
 */
enum LinkageFault {
    /** Nothing declares the class a reference names. */
    MISSING_CLASS(
            "JP1001",
            "referenced class is absent from the classpath and the runtime",
            "Neither the effective classpath nor the target runtime declares this class, and executable"
                    + " bytecode names it directly. The JVM resolves a class the first time that reference"
                    + " is reached, so nothing complains while the code sits unused and everything fails the"
                    + " moment it runs.",
            "Add the artifact that declares this class, or align the versions so the reference goes away."),

    /** The class resolves and declares no such field anywhere the JVM searches. */
    MISSING_FIELD(
            "JP1002",
            "referenced field is not declared anywhere above the resolved class",
            "The class this reference names resolves, and no field of this name and type is declared by it,"
                    + " by any of its superinterfaces, or by any class above it. The referencing bytecode was"
                    + " compiled against a version that did declare the field, and the version on the"
                    + " classpath no longer does.",
            "Restore the field, or align the versions so the compiled reference matches what ships."),

    /** The class resolves and declares no method with that exact descriptor. */
    MISSING_METHOD(
            "JP1003",
            "referenced method is not declared anywhere above the resolved class",
            "The class this reference names resolves, and no method of this name and descriptor is declared"
                    + " by it, by any class above it, or by any interface it inherits. A method whose"
                    + " parameters or return type changed is a different method to the JVM, so an overload"
                    + " that merely shares the name does not satisfy the call.",
            "Restore the method with the descriptor the call site compiled against, or align the versions."),

    /** The member resolves, and the reference and the declaration disagree about the receiver. */
    STATIC_MISMATCH(
            "JP1004",
            "reference and declaration disagree about a static member",
            "This member resolves, and one side treats it as belonging to the class while the other treats"
                    + " it as belonging to an instance. The referencing bytecode was compiled when the member"
                    + " sat on the other side of that line, and the JVM refuses the reference rather than"
                    + " inventing or discarding a receiver.",
            "Recompile the referencing artifact against the shipping version, or restore the member's"
                    + " original form."),

    /** The reference form and the resolved type disagree about class versus interface. */
    KIND_MISMATCH(
            "JP1005",
            "reference and resolved type disagree about class or interface",
            "An interface reference resolved to a class, or a class reference resolved to an interface. The"
                    + " JVM picks which resolution procedure to run from the form of the reference itself,"
                    + " before it looks for the member at all, so a mismatch is rejected outright instead of"
                    + " searched around.",
            "Recompile the referencing artifact so the reference form matches the type it names."),

    /** The class resolves and the referencing class may not see it. */
    INACCESSIBLE_CLASS(
            "JP1006",
            "resolved class is not accessible from the referencing class",
            "This class resolves and the referencing class is not allowed to see it, because the class is"
                    + " not public and the two do not share a run-time package. Accessibility is decided"
                    + " during resolution, so the reference fails even though the class is right there on"
                    + " the classpath.",
            "Publish the class, or move the referencing code into the package that owns it."),

    /** The member resolves and the referencing class may not see it. */
    INACCESSIBLE_MEMBER(
            "JP1007",
            "resolved member is not accessible from the referencing class",
            "This member resolves and its declared access shuts the referencing class out: private outside"
                    + " the nest, protected outside both the declaring package and the subclass hierarchy, or"
                    + " package-private across a package boundary. Accessibility is decided against the class"
                    + " that declares the member, not the class the reference named.",
            "Widen the member's access, or reach the same behaviour through API the referencing class may"
                    + " use.");

    private final FindingCode code;
    private final String summary;
    private final String explanation;
    private final String remediation;

    LinkageFault(String code, String summary, String explanation, String remediation) {
        this.code = FindingCode.of(code);
        this.summary = summary;
        this.explanation = explanation;
        this.remediation = remediation;
    }

    /**
     * Builds the finding for this fault.
     *
     * @param location the referencing class file, which is where a reader has to go to fix it
     * @param severity the tier this origin earns under the run's scope
     * @param subject the canonical symbol the fault is about
     * @param evidence the facts observed while resolving the reference
     * @return the complete finding
     */
    Finding at(ArtifactLocation location, Severity severity, String subject, List<Evidence> evidence) {
        return new Finding(
                code,
                severity,
                predictedError(),
                location,
                subject,
                summary,
                explanation,
                evidence,
                List.of(new Remediation(remediation)));
    }

    private PredictedError predictedError() {
        return switch (this) {
            case MISSING_CLASS -> PredictedError.NO_CLASS_DEF_FOUND_ERROR;
            case MISSING_FIELD -> PredictedError.NO_SUCH_FIELD_ERROR;
            case MISSING_METHOD -> PredictedError.NO_SUCH_METHOD_ERROR;
            case STATIC_MISMATCH, KIND_MISMATCH -> PredictedError.INCOMPATIBLE_CLASS_CHANGE_ERROR;
            case INACCESSIBLE_CLASS, INACCESSIBLE_MEMBER -> PredictedError.ILLEGAL_ACCESS_ERROR;
        };
    }
}
