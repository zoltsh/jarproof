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
 */
final class JsonReport {
    private static final String JARPROOF_JSON_VERSION = "jarproofJsonVersion";
    private static final String REQUEST = "request";
    private static final String FINDINGS = "findings";
    private static final String TOTAL = "total";

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
        appendSummary(json, result.findings());
        json.endObject();
        return json.document();
    }

    private static void appendSummary(JsonText json, List<Finding> findings) {
        json.name(FindingJson.SUMMARY).beginObject();
        for (Severity severity : Severity.values()) {
            json.name(CanonicalName.of(severity)).value(count(findings, severity));
        }
        json.name(TOTAL).value(findings.size());
        json.endObject();
    }

    private static int count(List<Finding> findings, Severity severity) {
        return (int) findings.stream().filter(finding -> finding.severity() == severity).count();
    }
}
