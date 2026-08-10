package sh.zolt.jarproof.api;

import java.util.List;
import java.util.Objects;

/**
 * One complete, immutable diagnostic: what is wrong, where it lives, which symbol it is about,
 * what the JVM will throw because of it, the facts that prove it, and the steps that fix it.
 *
 * <p>The subject is the canonical symbol the finding concerns — a class internal name such as
 * {@code com/acme/orders/OrderValidator}, or a member reference such as
 * {@code com/google/common/base/Preconditions#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V}.
 * Baselines fingerprint findings by code, location, and subject, so the subject must be stable
 * across runs and free of absolute paths.
 *
 * <p>Every finding is self-describing, so a renderer never needs to consult the engine to explain
 * one. The evidence and remediation lists are defensively copied and always unmodifiable.
 */
public record Finding(
        FindingCode code,
        Severity severity,
        PredictedError predictedError,
        ArtifactLocation artifact,
        String subject,
        String summary,
        String explanation,
        List<Evidence> evidence,
        List<Remediation> remediation) {
    public Finding {
        Objects.requireNonNull(code, "A finding needs a diagnostic code");
        Objects.requireNonNull(severity, "A finding needs a severity");
        Objects.requireNonNull(predictedError, "A finding needs a predicted runtime failure");
        Objects.requireNonNull(artifact, "A finding needs an artifact location");
        Objects.requireNonNull(subject, "A finding needs its subject symbol");
        Objects.requireNonNull(summary, "A finding needs a summary");
        Objects.requireNonNull(explanation, "A finding needs an explanation");
        Objects.requireNonNull(evidence, "A finding needs its evidence, even when empty");
        Objects.requireNonNull(remediation, "A finding needs its remediation, even when empty");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("A subject must name the class or member the finding is about");
        }
        if (summary.isBlank()) {
            throw new IllegalArgumentException("A summary must name the problem on one line");
        }
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("An explanation must describe why the problem occurs");
        }
        evidence = List.copyOf(evidence);
        remediation = List.copyOf(remediation);
    }
}
