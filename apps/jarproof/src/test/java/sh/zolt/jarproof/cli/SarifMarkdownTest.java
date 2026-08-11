package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * What a SARIF consumer that renders markdown sees, read back out of the document itself.
 *
 * <p>Every assertion here parses the rendered run with the CLI's own scanner and measures the
 * {@code markdown} member as a parsed string, so an expectation is written with real line breaks
 * instead of with the escapes the writer emits for them. The bytes of those escapes are pinned by the
 * document goldens in {@link SarifReportTest}; this file is about the formatting rules.
 */
final class SarifMarkdownTest {
    /** Every character the writer neutralises, separated so each one is visible on its own. */
    private static final String SIGNIFICANT = "\\ ` * _ [ ] < > #";

    /** The same characters, each preceded by a backslash. */
    private static final String NEUTRALISED = "\\\\ \\` \\* \\_ \\[ \\] \\< \\> \\#";

    private static final String SUMMARY = "one";
    private static final String EXPLANATION = "two";

    @Test
    void rendersTheWholeFindingAsMarkdown() {
        assertEquals(
                """
                **missing method**

                guava-18.0.jar declares no checkArgument(boolean, String, Object) on Preconditions.

                - selected guava-18.0.jar from lib/\\*
                - app.jar was compiled against a newer guava

                **next:** align the runtime classpath with the version used to compile app.jar""",
                markdownOf(SampleFindings.missingMethod()));
    }

    @Test
    void escapesEveryMarkdownSignificantCharacterOfTheSummaryAndTheExplanation() {
        assertEquals(
                "**" + NEUTRALISED + "**\n\n" + NEUTRALISED,
                markdownOf(specimen(SIGNIFICANT, SIGNIFICANT, List.of(), List.of())));
    }

    @Test
    void escapesTheDataOfEveryBulletAndOfTheNextLine() {
        assertEquals(
                "**" + SUMMARY + "**\n\n" + EXPLANATION + "\n\n- " + NEUTRALISED
                        + "\n\n**next:** " + NEUTRALISED,
                markdownOf(specimen(SUMMARY, EXPLANATION,
                        List.of(new Evidence(SIGNIFICANT)), List.of(new Remediation(SIGNIFICANT)))));
    }

    @Test
    void writesNoListAndNoNextLineForAFindingThatHasNeither() {
        assertEquals(
                """
                **split package across artifacts**

                The package is assembled from more than one artifact, which a classpath allows.""",
                markdownOf(SampleFindings.splitPackage()));
    }

    @Test
    void writesTheListWithoutANextLineWhenThereIsNoRemediation() {
        assertEquals(
                "**" + SUMMARY + "**\n\n" + EXPLANATION + "\n\n- three",
                markdownOf(specimen(SUMMARY, EXPLANATION, List.of(new Evidence("three")), List.of())));
    }

    @Test
    void rendersTheSameBytesOnEveryRun() {
        VerificationResult result = SampleFindings.result(SampleFindings.missingMethod());

        assertEquals(SarifReport.render(result), SarifReport.render(result));
    }

    /** A finding carrying exactly the prose and lists a case is about. */
    private static Finding specimen(
            String summary, String explanation, List<Evidence> evidence, List<Remediation> remediation) {
        return new Finding(
                FindingCode.of("JP1003"),
                Severity.ERROR,
                PredictedError.NO_SUCH_METHOD_ERROR,
                ArtifactLocation.ofArtifact(SampleFindings.APPLICATION),
                "com/acme/api/Ledger#post()V",
                summary,
                explanation,
                evidence,
                remediation);
    }

    /** The markdown of the only result in a run rendered from one finding. */
    private static String markdownOf(Finding finding) {
        String document = SarifReport.render(SampleFindings.result(finding));
        Map<?, ?> run = object(array(object(JsonScanner.parse(document)).get("runs")).get(0));
        Map<?, ?> result = object(array(run.get("results")).get(0));
        return (String) object(result.get("message")).get("markdown");
    }

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        return (List<?>) value;
    }
}
