package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VerificationResultTest {
    @Test
    void keepsTheSuppliedFindings() {
        Finding finding = finding(Severity.WARNING);
        VerificationResult result = new VerificationResult(List.of(finding));

        assertEquals(List.of(finding), result.findings());
    }

    @Test
    void reportsErrorsWhenAnyFindingIsAnError() {
        VerificationResult result =
                new VerificationResult(List.of(finding(Severity.INFO), finding(Severity.ERROR)));

        assertTrue(result.hasErrors());
    }

    @Test
    void reportsNoErrorsBelowErrorSeverity() {
        VerificationResult result =
                new VerificationResult(List.of(finding(Severity.INFO), finding(Severity.WARNING)));

        assertFalse(result.hasErrors());
    }

    @Test
    void reportsNoErrorsForACleanRun() {
        VerificationResult result = new VerificationResult(List.of());

        assertEquals(List.of(), result.findings());
        assertFalse(result.hasErrors());
    }

    @Test
    void copiesTheFindingsDefensively() {
        List<Finding> findings = new ArrayList<>(List.of(finding(Severity.INFO)));
        VerificationResult result = new VerificationResult(findings);

        findings.add(finding(Severity.ERROR));

        assertEquals(1, result.findings().size());
        assertFalse(result.hasErrors());
    }

    @Test
    void publishesUnmodifiableFindings() {
        List<Finding> findings = new VerificationResult(List.of(finding(Severity.INFO))).findings();
        Finding extra = finding(Severity.ERROR);

        assertThrows(UnsupportedOperationException.class, () -> findings.add(extra));
    }

    @Test
    void rejectsMissingFindings() {
        assertThrows(NullPointerException.class, () -> new VerificationResult(null));
    }

    @Test
    void comparesByFindings() {
        VerificationResult result = new VerificationResult(List.of(finding(Severity.INFO)));

        assertEquals(new VerificationResult(List.of(finding(Severity.INFO))), result);
        assertEquals(new VerificationResult(List.of(finding(Severity.INFO))).hashCode(), result.hashCode());
    }

    private static Finding finding(Severity severity) {
        return new Finding(
                FindingCode.of("JP2002"),
                severity,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact("lib/guava-18.0.jar"),
                "com/google/common/collect/ImmutableList",
                "duplicate class",
                "Two artifacts declare the same class with differing bytecode.",
                List.of(new Evidence("guava-18.0.jar and guava-33.0.0-jre.jar both declare it")),
                List.of(new Remediation("remove one of the two artifacts")));
    }
}
