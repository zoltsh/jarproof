package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FindingTest {
    private static final FindingCode CODE = FindingCode.of("JP1003");
    private static final Severity SEVERITY = Severity.ERROR;
    private static final PredictedError PREDICTED = PredictedError.NO_SUCH_METHOD_ERROR;
    private static final ArtifactLocation LOCATION =
            ArtifactLocation.ofClassEntry("app.jar", "com/acme/orders/OrderValidator.class");
    private static final String SUBJECT =
            "com/google/common/base/Preconditions#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V";
    private static final String SUMMARY = "missing method";
    private static final String EXPLANATION = "The selected artifact no longer declares the referenced method.";
    private static final List<Evidence> EVIDENCE = List.of(new Evidence("selected guava-18.0.jar"));
    private static final List<Remediation> REMEDIATION = List.of(new Remediation("align the runtime classpath"));

    @Test
    void keepsEverySuppliedComponent() {
        Finding finding = sample();

        assertEquals(CODE, finding.code());
        assertEquals(SEVERITY, finding.severity());
        assertEquals(PREDICTED, finding.predictedError());
        assertEquals(LOCATION, finding.artifact());
        assertEquals(SUBJECT, finding.subject());
        assertEquals(SUMMARY, finding.summary());
        assertEquals(EXPLANATION, finding.explanation());
        assertEquals(EVIDENCE, finding.evidence());
        assertEquals(REMEDIATION, finding.remediation());
    }

    @Test
    void copiesEvidenceAndRemediationDefensively() {
        List<Evidence> evidence = new ArrayList<>(EVIDENCE);
        List<Remediation> remediation = new ArrayList<>(REMEDIATION);
        Finding finding = new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, evidence, remediation);

        evidence.add(new Evidence("added after construction"));
        remediation.add(new Remediation("added after construction"));

        assertEquals(EVIDENCE, finding.evidence());
        assertEquals(REMEDIATION, finding.remediation());
    }

    @Test
    void publishesUnmodifiableEvidence() {
        List<Evidence> evidence = sample().evidence();
        Evidence extra = new Evidence("late evidence");

        assertThrows(UnsupportedOperationException.class, () -> evidence.add(extra));
    }

    @Test
    void publishesUnmodifiableRemediation() {
        List<Remediation> remediation = sample().remediation();
        Remediation extra = new Remediation("late remediation");

        assertThrows(UnsupportedOperationException.class, () -> remediation.add(extra));
    }

    @Test
    void rejectsAnyMissingComponent() {
        assertThrows(NullPointerException.class, () -> new Finding(
                null, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, null, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, null, LOCATION, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, null, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, null, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, null, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, null, EVIDENCE, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, null, REMEDIATION));
        assertThrows(NullPointerException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, null));
    }

    @Test
    void rejectsABlankSubject() {
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, "", SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, "\t", SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
    }

    @Test
    void rejectsABlankSummary() {
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, "", EXPLANATION, EVIDENCE, REMEDIATION));
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, "  ", EXPLANATION, EVIDENCE, REMEDIATION));
    }

    @Test
    void rejectsABlankExplanation() {
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, "", EVIDENCE, REMEDIATION));
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, "\n", EVIDENCE, REMEDIATION));
    }

    @Test
    void acceptsFindingsWithoutEvidenceOrRemediation() {
        Finding finding = new Finding(
                CODE, Severity.INFO, PredictedError.NONE, ArtifactLocation.ofArtifact("app.jar"),
                "com/acme/orders/OrderValidator", SUMMARY, EXPLANATION, List.of(), List.of());

        assertEquals(List.of(), finding.evidence());
        assertEquals(List.of(), finding.remediation());
    }

    @Test
    void comparesByEveryComponent() {
        Finding finding = sample();

        assertEquals(finding, sample());
        assertEquals(finding.hashCode(), sample().hashCode());
        assertNotEquals(finding, new Finding(
                CODE, Severity.WARNING, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION));
        assertNotEquals(finding, new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, List.of(), REMEDIATION));
    }

    private static Finding sample() {
        return new Finding(
                CODE, SEVERITY, PREDICTED, LOCATION, SUBJECT, SUMMARY, EXPLANATION, EVIDENCE, REMEDIATION);
    }
}
