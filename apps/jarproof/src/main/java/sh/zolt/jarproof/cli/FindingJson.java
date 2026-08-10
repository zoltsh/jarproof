package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Remediation;

/**
 * One finding as a JSON v1 object.
 *
 * <p>Member order is part of the contract: {@code code}, {@code severity}, {@code predictedError},
 * {@code artifact}, {@code subject}, {@code summary}, {@code explanation}, {@code evidence},
 * {@code remediation}. {@code classEntry} appears inside {@code artifact} only when the finding
 * concerns a single class, because an absent entry and an empty entry mean different things.
 * {@code predictedError} is always present and names the throwable, or {@code None}.
 */
final class FindingJson {
    /** Member holding the summary of a finding, and of a whole run. */
    static final String SUMMARY = "summary";

    /** Member holding an artifact path, and the label a report prints one under. */
    static final String ARTIFACT = "artifact";

    private static final String CODE = "code";
    private static final String SEVERITY = "severity";
    private static final String PREDICTED_ERROR = "predictedError";
    private static final String CLASS_ENTRY = "classEntry";
    private static final String SUBJECT = "subject";
    private static final String EXPLANATION = "explanation";
    private static final String EVIDENCE = "evidence";
    private static final String REMEDIATION = "remediation";

    private FindingJson() {
    }

    /**
     * Writes one finding as an element of the array already open.
     *
     * @param json the writer positioned inside an array
     * @param finding the finding to render
     */
    static void append(JsonText json, Finding finding) {
        json.beginObject();
        json.name(CODE).value(finding.code().value());
        json.name(SEVERITY).value(CanonicalName.of(finding.severity()));
        json.name(PREDICTED_ERROR).value(finding.predictedError().value());
        appendLocation(json, finding);
        json.name(SUBJECT).value(finding.subject());
        json.name(SUMMARY).value(finding.summary());
        json.name(EXPLANATION).value(finding.explanation());
        json.name(EVIDENCE).beginArray();
        for (Evidence evidence : finding.evidence()) {
            json.value(evidence.detail());
        }
        json.endArray();
        json.name(REMEDIATION).beginArray();
        for (Remediation remediation : finding.remediation()) {
            json.value(remediation.action());
        }
        json.endArray();
        json.endObject();
    }

    private static void appendLocation(JsonText json, Finding finding) {
        json.name(ARTIFACT).beginObject();
        json.name(ARTIFACT).value(finding.artifact().artifact());
        finding.artifact().classEntry().ifPresent(entry -> json.name(CLASS_ENTRY).value(entry));
        json.endObject();
    }
}
