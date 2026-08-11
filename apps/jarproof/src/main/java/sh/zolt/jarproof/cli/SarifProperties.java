package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Remediation;

/**
 * The {@code properties} bag of a SARIF result: the finding's own evidence and remediation.
 *
 * <p>A property bag is the extension point SARIF 2.1.0 defines for exactly this, so the two lists a
 * finding carries reach a consumer as data rather than only as prose inside a message. The member
 * names are the ones JSON v1 already uses ({@link FindingJson}), because the values are the same
 * values: a tool reading both formats reads one vocabulary, and a baseline, a JSON report, and a SARIF
 * result cannot drift into three spellings of one fact.
 *
 * <p>Both members are always written, empty arrays included. A finding legitimately has no evidence or
 * no remediation -- an informational split package has neither -- and a consumer that has to
 * distinguish "absent" from "empty" to read a list is a consumer with two code paths for one answer.
 *
 * <p>The strings are whatever the caller handed in, which for a machine format means already measured
 * from {@code --path-root}: {@link PathRoot} rewrote the path spellings inside the evidence before any
 * of it reached here, so the bag names the same artifacts the result's own location does.
 */
final class SarifProperties {
    private static final String PROPERTIES = "properties";

    private SarifProperties() {
    }

    /**
     * Writes the {@code properties} member of the result object already open.
     *
     * @param json the writer positioned inside a result object
     * @param finding the finding whose evidence and remediation the bag carries
     */
    static void append(JsonText json, Finding finding) {
        json.name(PROPERTIES).beginObject();
        json.name(FindingJson.EVIDENCE).beginArray();
        for (Evidence evidence : finding.evidence()) {
            json.value(evidence.detail());
        }
        json.endArray();
        json.name(FindingJson.REMEDIATION).beginArray();
        for (Remediation remediation : finding.remediation()) {
            json.value(remediation.action());
        }
        json.endArray();
        json.endObject();
    }
}
