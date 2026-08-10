package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

final class ReportFormatTest {
    @Test
    void parsesEveryFormatAndRejectsAnythingElse() {
        assertEquals(Optional.of(ReportFormat.HUMAN), ReportFormat.parse("human"));
        assertEquals(Optional.of(ReportFormat.JSON), ReportFormat.parse("json"));
        assertEquals(Optional.of(ReportFormat.SARIF), ReportFormat.parse("sarif"));
        assertTrue(ReportFormat.parse("xml").isEmpty());
        assertTrue(ReportFormat.parse("Human").isEmpty());
    }

    @Test
    void rendersEachFormatThroughItsOwnWriter() {
        VerificationRequest request = SampleFindings.request();
        VerificationResult result = SampleFindings.result(SampleFindings.missingMethod());

        assertEquals(HumanReport.render(result), ReportFormat.HUMAN.render(request, result));
        assertEquals(JsonReport.render(request, result), ReportFormat.JSON.render(request, result));
        assertEquals(SarifReport.render(result), ReportFormat.SARIF.render(request, result));
    }

    @Test
    void endsEveryFormatWithExactlyOneLineFeed() {
        VerificationRequest request = SampleFindings.request();
        VerificationResult result = SampleFindings.result(SampleFindings.splitPackage());

        for (ReportFormat format : ReportFormat.values()) {
            String report = format.render(request, result);
            assertTrue(report.endsWith("\n"), report);
            assertTrue(!report.endsWith("\n\n"), report);
            assertEquals(-1, report.indexOf('\r'), report);
        }
    }
}
