package sh.zolt.jarproof.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * A minimal, valid SARIF 2.1.0 document for one verification run.
 *
 * <p>The document holds a single run whose driver is jarproof and whose rules are the distinct
 * diagnostic codes the run actually produced -- codes that did not fire are not advertised. A
 * rule's short description is the summary of the first finding that raised it, so the rule text
 * comes from the same words the human report shows. Every string obeys the canonical rules in
 * {@link JsonText}, which is what lets the native binary and the JVM be compared byte for byte.
 *
 * <p>Source roots reach the document here and nowhere else, because SARIF is the only format that
 * resolves them ({@link SarifResult}). A run rendered without roots keeps every result at artifact
 * level, which is what the format promised before source mapping existed.
 */
final class SarifReport {
    private static final String SARIF_VERSION = "2.1.0";
    private static final String SCHEMA = "$schema";
    private static final String SCHEMA_URI =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
    private static final String RUNS = "runs";
    private static final String DRIVER = "driver";
    private static final String RULES = "rules";
    private static final String ID = "id";
    private static final String SHORT_DESCRIPTION = "shortDescription";

    private SarifReport() {
    }

    /**
     * Renders one verification run whose results stay at artifact level.
     *
     * @param result the findings the run produced
     * @return the complete document, terminated by a single LF
     */
    static String render(VerificationResult result) {
        return render(result, List.of());
    }

    /**
     * Renders one verification run as a SARIF 2.1.0 document.
     *
     * @param result the findings the run produced
     * @param sourceRoots roots a result's source path may be resolved against, in caller order
     * @return the complete document, terminated by a single LF
     */
    static String render(VerificationResult result, List<Path> sourceRoots) {
        JsonText json = new JsonText();
        json.beginObject();
        json.name(ToolJson.VERSION).value(SARIF_VERSION);
        json.name(SCHEMA).value(SCHEMA_URI);
        json.name(RUNS).beginArray();
        json.beginObject();
        json.name(ToolJson.TOOL).beginObject();
        json.name(DRIVER).beginObject();
        ToolJson.appendIdentity(json);
        appendRules(json, result.findings());
        json.endObject();
        json.endObject();
        SarifResult.appendResults(json, result.findings(), sourceRoots);
        json.endObject();
        json.endArray();
        json.endObject();
        return json.document();
    }

    private static void appendRules(JsonText json, List<Finding> findings) {
        json.name(RULES).beginArray();
        for (Map.Entry<String, String> rule : describeRules(findings).entrySet()) {
            json.beginObject();
            json.name(ID).value(rule.getKey());
            json.name(SHORT_DESCRIPTION).beginObject();
            json.name(SarifResult.TEXT).value(rule.getValue());
            json.endObject();
            json.endObject();
        }
        json.endArray();
    }

    private static Map<String, String> describeRules(List<Finding> findings) {
        Map<String, String> rules = new LinkedHashMap<>();
        for (Finding finding : findings) {
            rules.putIfAbsent(finding.code().value(), finding.summary());
        }
        return rules;
    }
}
