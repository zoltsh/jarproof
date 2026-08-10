package sh.zolt.jarproof.cli;

import java.nio.file.Path;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/** Canned findings shared by the reporting tests, modelled on the diagnostics in DESIGN.md. */
final class SampleFindings {
    private SampleFindings() {
    }

    /** The DESIGN section 1 specimen: an application-origin missing method. */
    static Finding missingMethod() {
        return new Finding(
                FindingCode.of("JP1003"),
                Severity.ERROR,
                PredictedError.NO_SUCH_METHOD_ERROR,
                ArtifactLocation.ofClassEntry("app.jar", "com/acme/orders/OrderValidator.class"),
                "com/google/common/base/Preconditions#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V",
                "missing method",
                "guava-18.0.jar declares no checkArgument(boolean, String, Object) on Preconditions.",
                List.of(
                        new Evidence("selected guava-18.0.jar from lib/*"),
                        new Evidence("app.jar was compiled against a newer guava")),
                List.of(
                        new Remediation("align the runtime classpath with the version used to compile app.jar"),
                        new Remediation("or recompile app.jar against guava-18.0.jar")));
    }

    /** A library-only duplicate: warning severity, no predicted throwable, no class entry. */
    static Finding duplicateClass() {
        return new Finding(
                FindingCode.of("JP2002"),
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact("lib/commons-io-2.4.jar"),
                "org/apache/commons/io/IOUtils",
                "duplicate class with differing bytecode",
                "Two artifacts declare the class with different content, so one definition is dead.",
                List.of(new Evidence("also declared by lib/commons-io-2.11.0.jar")),
                List.of(new Remediation("remove one of the two commons-io artifacts")));
    }

    /** An informational finding with neither evidence nor remediation. */
    static Finding splitPackage() {
        return new Finding(
                FindingCode.of("JP2003"),
                Severity.INFO,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact("lib/extras.jar"),
                "com/acme/orders",
                "split package across artifacts",
                "The package is assembled from more than one artifact, which a classpath allows.",
                List.of(),
                List.of());
    }

    /** The request the sample findings answer. */
    static VerificationRequest request() {
        return new VerificationRequest(
                List.of(Path.of("app.jar")),
                List.of(Path.of("lib")),
                TargetRuntime.of(17),
                Scope.APPLICATION);
    }

    /** Wraps findings as a result, in the order given. */
    static VerificationResult result(Finding... findings) {
        return new VerificationResult(List.of(findings));
    }
}
