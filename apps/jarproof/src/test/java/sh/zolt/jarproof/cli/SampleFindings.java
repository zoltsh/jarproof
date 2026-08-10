package sh.zolt.jarproof.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
    /** The application every specimen is reported against. */
    static final String APPLICATION = "app.jar";

    /** The class entry the missing-method specimen is located in. */
    static final String CLASS_ENTRY = "com/acme/orders/OrderValidator.class";

    /** The {@code SourceFile} attribute that class entry would carry, compiled with debug information. */
    static final String SOURCE_FILE = "OrderValidator.java";

    /** Where that source file sits relative to a source root, which is the class's package directory. */
    static final String SOURCE_PATH = "com/acme/orders/OrderValidator.java";

    /** The line the specimen's broken call is written on. */
    static final int SOURCE_LINE = 42;

    private SampleFindings() {
    }

    /** The DESIGN section 1 specimen: an application-origin missing method. */
    static Finding missingMethod() {
        return new Finding(
                FindingCode.of("JP1003"),
                Severity.ERROR,
                PredictedError.NO_SUCH_METHOD_ERROR,
                ArtifactLocation.ofClassEntry(APPLICATION, CLASS_ENTRY),
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

    /**
     * The same missing method, from a class file that recorded where it was compiled from.
     *
     * @param line the source line the call is written on, absent when no line table covers it
     * @return the specimen finding, located in source as far as the class file allows
     */
    static Finding missingMethodInSource(Optional<Integer> line) {
        Finding located = missingMethod();
        return new Finding(
                located.code(),
                located.severity(),
                located.predictedError(),
                ArtifactLocation.ofSource(APPLICATION, CLASS_ENTRY, Optional.of(SOURCE_FILE), line),
                located.subject(),
                located.summary(),
                located.explanation(),
                located.evidence(),
                located.remediation());
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
        return VerificationRequest.of(
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
