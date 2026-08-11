package sh.zolt.jarproof.cli;

import java.util.List;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The versioned JSON v1 envelope: jarproof's machine contract.
 *
 * <p>Member order is fixed at {@code jarproofJsonVersion}, {@code tool}, {@code request},
 * {@code findings}, {@code summary}, and findings are emitted in the order the engine produced
 * them, which is already canonical. The summary counts every severity, including the ones that did
 * not occur, so a consumer can read a zero instead of inferring one from a missing key.
 *
 * <p>{@code analyzedClasses} and {@code analyzedArtifacts} follow {@code total} and say how much the
 * run examined, which is what lets a consumer tell a clean report from a report of nothing. They are
 * optional members of a version that already exists, which the format allows: they arrive after the
 * keys a consumer already reads, and a result that states no tallies omits both rather than claiming
 * zero work. Anything the engine ran states them.
 */
final class JsonReport {
    private static final String JARPROOF_JSON_VERSION = "jarproofJsonVersion";
    private static final String REQUEST = "request";
    private static final String FINDINGS = "findings";
    private static final String TOTAL = "total";
    private static final String ANALYZED_CLASSES = "analyzedClasses";
    private static final String ANALYZED_ARTIFACTS = "analyzedArtifacts";

    private JsonReport() {
    }

    /**
     * Renders one verification run as a canonical JSON v1 document.
     *
     * @param request the request the run answered
     * @param result the findings the run produced
     * @return the complete document, terminated by a single LF
     */
    static String render(VerificationRequest request, VerificationResult result) {
        JsonText json = new JsonText();
        json.beginObject();
        json.name(JARPROOF_JSON_VERSION).value(ProductIdentity.JSON_VERSION);
        json.name(ToolJson.TOOL).beginObject();
        ToolJson.appendIdentity(json);
        json.endObject();
        json.name(REQUEST).beginObject();
        RequestJson.appendFields(
                json,
                request.targetRuntime().javaRelease(),
                request.targetRuntime().previewMode(),
                request.scope());
        json.endObject();
        json.name(FINDINGS).beginArray();
        for (Finding finding : result.findings()) {
            FindingJson.append(json, finding);
        }
        json.endArray();
        appendSummary(json, result);
        json.endObject();
        return json.document();
    }

    private static void appendSummary(JsonText json, VerificationResult result) {
        List<Finding> findings = result.findings();
        json.name(FindingJson.SUMMARY).beginObject();
        for (Severity severity : Severity.values()) {
            json.name(CanonicalName.of(severity)).value(count(findings, severity));
        }
        json.name(TOTAL).value(findings.size());
        appendExamined(json, result);
        json.endObject();
    }

    /** Writes what the run examined, or nothing at all when the result states no tallies. */
    private static void appendExamined(JsonText json, VerificationResult result) {
        if (result.analyzedClassCount() == 0 && result.analyzedArtifactCount() == 0) {
            return;
        }
        json.name(ANALYZED_CLASSES).value(result.analyzedClassCount());
        json.name(ANALYZED_ARTIFACTS).value(result.analyzedArtifactCount());
    }

    private static int count(List<Finding> findings, Severity severity) {
        return (int) findings.stream().filter(finding -> finding.severity() == severity).count();
    }
}
