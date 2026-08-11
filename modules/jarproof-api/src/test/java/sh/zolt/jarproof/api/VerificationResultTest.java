package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VerificationResultTest {
    private static final int CLASSES = 5000;
    private static final int ARTIFACTS = 40;

    @Test
    void keepsTheSuppliedFindings() {
        Finding finding = finding(Severity.WARNING);
        VerificationResult result = VerificationResult.of(List.of(finding));

        assertEquals(List.of(finding), result.findings());
    }

    @Test
    void keepsTheSuppliedTallies() {
        VerificationResult result = new VerificationResult(List.of(), CLASSES, ARTIFACTS);

        assertEquals(CLASSES, result.analyzedClassCount());
        assertEquals(ARTIFACTS, result.analyzedArtifactCount());
    }

    /** A result assembled without tallies states none, which is what zero means here. */
    @Test
    void statesNoTalliesWhenNoneAreKnown() {
        VerificationResult result = VerificationResult.of(List.of(finding(Severity.INFO)));

        assertEquals(0, result.analyzedClassCount());
        assertEquals(0, result.analyzedArtifactCount());
        assertEquals(1, result.findings().size());
    }

    /** A run may legitimately examine positions that present no classes at all. */
    @Test
    void acceptsARunThatFoundClassesInNoneOfItsArtifacts() {
        VerificationResult result = new VerificationResult(List.of(), 0, 1);

        assertEquals(0, result.analyzedClassCount());
        assertEquals(1, result.analyzedArtifactCount());
    }

    @Test
    void reportsErrorsWhenAnyFindingIsAnError() {
        VerificationResult result =
                VerificationResult.of(List.of(finding(Severity.INFO), finding(Severity.ERROR)));

        assertTrue(result.hasErrors());
    }

    @Test
    void reportsNoErrorsBelowErrorSeverity() {
        VerificationResult result =
                VerificationResult.of(List.of(finding(Severity.INFO), finding(Severity.WARNING)));

        assertFalse(result.hasErrors());
    }

    @Test
    void reportsNoErrorsForACleanRun() {
        VerificationResult result = VerificationResult.of(List.of());

        assertEquals(List.of(), result.findings());
        assertFalse(result.hasErrors());
    }

    @Test
    void copiesTheFindingsDefensively() {
        List<Finding> findings = new ArrayList<>(List.of(finding(Severity.INFO)));
        VerificationResult result = new VerificationResult(findings, CLASSES, ARTIFACTS);

        findings.add(finding(Severity.ERROR));

        assertEquals(1, result.findings().size());
        assertFalse(result.hasErrors());
    }

    @Test
    void publishesUnmodifiableFindings() {
        List<Finding> findings = VerificationResult.of(List.of(finding(Severity.INFO))).findings();
        Finding extra = finding(Severity.ERROR);

        assertThrows(UnsupportedOperationException.class, () -> findings.add(extra));
    }

    @Test
    void rejectsMissingFindings() {
        assertThrows(NullPointerException.class, () -> VerificationResult.of(null));
        assertThrows(NullPointerException.class, () -> new VerificationResult(null, CLASSES, ARTIFACTS));
    }

    @Test
    void rejectsATallyNoRunCouldHaveProduced() {
        List<Finding> findings = List.of(finding(Severity.INFO));

        assertThrows(
                IllegalArgumentException.class, () -> new VerificationResult(findings, -1, ARTIFACTS));
        assertThrows(IllegalArgumentException.class, () -> new VerificationResult(findings, CLASSES, -1));
    }

    @Test
    void comparesByFindingsAndByTallies() {
        VerificationResult result = new VerificationResult(List.of(finding(Severity.INFO)), CLASSES, ARTIFACTS);

        assertEquals(new VerificationResult(List.of(finding(Severity.INFO)), CLASSES, ARTIFACTS), result);
        assertEquals(
                new VerificationResult(List.of(finding(Severity.INFO)), CLASSES, ARTIFACTS).hashCode(),
                result.hashCode());
        assertNotEquals(VerificationResult.of(List.of(finding(Severity.INFO))), result);
        assertNotEquals(new VerificationResult(List.of(finding(Severity.INFO)), CLASSES, 1), result);
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
