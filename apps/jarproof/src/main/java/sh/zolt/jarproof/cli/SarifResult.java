package sh.zolt.jarproof.cli;

import java.util.List;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

/**
 * The {@code results} array of a SARIF run.
 *
 * <p>Every finding becomes one result carrying its rule id, its level, its summary as the message,
 * and an artifact-level location. Jarproof's {@code info} severity maps to the SARIF level
 * {@code note}; {@code warning} and {@code error} keep their names. Source-line mapping is
 * deliberately absent until it can be trusted, so a location names the artifact and nothing more
 * precise than jarproof can prove.
 */
final class SarifResult {
    /** Member holding a human-readable string inside a message or description. */
    static final String TEXT = "text";

    private static final String RESULTS = "results";
    private static final String RULE_ID = "ruleId";
    private static final String LEVEL = "level";
    private static final String MESSAGE = "message";
    private static final String LOCATIONS = "locations";
    private static final String PHYSICAL_LOCATION = "physicalLocation";
    private static final String ARTIFACT_LOCATION = "artifactLocation";
    private static final String URI = "uri";
    private static final String NOTE = "note";

    private SarifResult() {
    }

    /**
     * Writes the {@code results} member of the run object already open.
     *
     * @param json the writer positioned inside the run object
     * @param findings the findings to render, in the order given
     */
    static void appendResults(JsonText json, List<Finding> findings) {
        json.name(RESULTS).beginArray();
        for (Finding finding : findings) {
            appendResult(json, finding);
        }
        json.endArray();
    }

    /**
     * Returns the SARIF level for a jarproof severity.
     *
     * @param severity the finding's severity
     * @return {@code note} for informational findings, otherwise the severity's own name
     */
    static String levelOf(Severity severity) {
        return severity == Severity.INFO ? NOTE : CanonicalName.of(severity);
    }

    private static void appendResult(JsonText json, Finding finding) {
        json.beginObject();
        json.name(RULE_ID).value(finding.code().value());
        json.name(LEVEL).value(levelOf(finding.severity()));
        json.name(MESSAGE).beginObject();
        json.name(TEXT).value(finding.summary());
        json.endObject();
        appendLocation(json, finding);
        json.endObject();
    }

    private static void appendLocation(JsonText json, Finding finding) {
        json.name(LOCATIONS).beginArray();
        json.beginObject();
        json.name(PHYSICAL_LOCATION).beginObject();
        json.name(ARTIFACT_LOCATION).beginObject();
        json.name(URI).value(finding.artifact().artifact());
        json.endObject();
        json.endObject();
        json.endObject();
        json.endArray();
    }
}
