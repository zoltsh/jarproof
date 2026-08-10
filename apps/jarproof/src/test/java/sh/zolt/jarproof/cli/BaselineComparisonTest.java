package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.VerificationResult;

final class BaselineComparisonTest {
    @Test
    void reportsOnlyFindingsTheBaselineDoesNotAccept() {
        Finding accepted = SampleFindings.duplicateClass();
        Finding fresh = SampleFindings.missingMethod();
        BaselineDocument baseline = baselineOf(accepted);

        BaselineComparison comparison =
                BaselineComparison.against(SampleFindings.result(accepted, fresh), baseline);

        assertEquals(List.of(fresh), comparison.newFindings());
        assertEquals(1, comparison.suppressed());
        assertEquals(List.of(), comparison.stale());
    }

    @Test
    void suppressesEveryFindingOfAnUnchangedRun() {
        VerificationResult result =
                SampleFindings.result(SampleFindings.missingMethod(), SampleFindings.splitPackage());
        BaselineDocument baseline = BaselineDocument.of(SampleFindings.request(), "jdk", result);

        BaselineComparison comparison = BaselineComparison.against(result, baseline);

        assertEquals(List.of(), comparison.newFindings());
        assertEquals(2, comparison.suppressed());
        assertEquals(List.of(), comparison.stale());
    }

    @Test
    void reportsAcceptedFingerprintsThatNoLongerOccur() {
        BaselineDocument baseline = new BaselineDocument(
                17,
                PreviewMode.DISABLED,
                Scope.APPLICATION,
                "jdk",
                List.of("JP1001|gone.jar||com/acme/Gone", BaselineFingerprint.of(SampleFindings.splitPackage())));

        BaselineComparison comparison =
                BaselineComparison.against(SampleFindings.result(SampleFindings.splitPackage()), baseline);

        assertEquals(List.of(), comparison.newFindings());
        assertEquals(1, comparison.suppressed());
        assertEquals(List.of("JP1001|gone.jar||com/acme/Gone"), comparison.stale());
    }

    @Test
    void treatsAnEmptyBaselineAsAcceptingNothing() {
        VerificationResult result = SampleFindings.result(SampleFindings.missingMethod());
        BaselineDocument baseline = new BaselineDocument(
                17, PreviewMode.DISABLED, Scope.APPLICATION, "jdk", List.of());

        BaselineComparison comparison = BaselineComparison.against(result, baseline);

        assertEquals(result.findings(), comparison.newFindings());
        assertEquals(0, comparison.suppressed());
    }

    @Test
    void countsRepeatedFindingsOnceInTheStaleCheck() {
        Finding repeated = SampleFindings.missingMethod();
        BaselineComparison comparison =
                BaselineComparison.against(SampleFindings.result(repeated, repeated), baselineOf(repeated));

        assertEquals(List.of(), comparison.newFindings());
        assertEquals(2, comparison.suppressed());
        assertEquals(List.of(), comparison.stale());
    }

    @Test
    void refusesToDescribeAnImpossibleComparison() {
        assertThrows(NullPointerException.class, () -> new BaselineComparison(null, 0, List.of()));
        assertThrows(NullPointerException.class, () -> new BaselineComparison(List.of(), 0, null));
        assertEquals(
                "A suppressed count cannot be negative: -1",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new BaselineComparison(List.of(), -1, List.of()))
                        .getMessage());
    }

    private static BaselineDocument baselineOf(Finding... findings) {
        return BaselineDocument.of(SampleFindings.request(), "jdk", SampleFindings.result(findings));
    }
}
