package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * One artifact's facts as JSON, for a script that would rather not parse a table.
 *
 * <p><strong>This shape is unstable in 0.1.x.</strong> Only the {@code check} envelope is a versioned
 * contract; {@code inspect} exists to answer questions nobody has finished asking, so its members may
 * be renamed, regrouped, or dropped in any 0.1 release. It carries no version member precisely so
 * that nobody can mistake it for a promise.
 *
 * <p>What is guaranteed is the canonical form every jarproof document shares: the member order
 * written here, LF endings, and the escaping rules in {@link JsonText}. Two runs over the same
 * artifact produce the same bytes.
 */
final class ArtifactJson {
    private static final String ENTRY_COUNT = "entryCount";
    private static final String CLASS_COUNT = "classCount";
    private static final String BYTECODE_LEVELS = "bytecodeLevels";
    private static final String DECLARED_SERVICES = "declaredServices";
    private static final String MULTI_RELEASE_VERSIONS = "multiReleaseVersions";

    private ArtifactJson() {
    }

    /**
     * Renders one artifact's facts as a canonical JSON document.
     *
     * @param summary the facts the inspection read
     * @return the complete document, terminated by a single LF
     */
    static String render(ArtifactSummary summary) {
        JsonText json = new JsonText();
        json.beginObject();
        json.name(FindingJson.ARTIFACT).value(summary.artifact());
        json.name(ENTRY_COUNT).value(summary.entryCount());
        json.name(CLASS_COUNT).value(summary.classCount());
        json.name(BYTECODE_LEVELS).beginArray();
        summary.bytecodeLevels().forEach(json::value);
        json.endArray();
        json.name(DECLARED_SERVICES).beginArray();
        summary.declaredServices().forEach(json::value);
        json.endArray();
        json.name(MULTI_RELEASE_VERSIONS).beginArray();
        for (int release : summary.multiReleaseVersions()) {
            json.value(release);
        }
        json.endArray();
        json.endObject();
        return json.document();
    }
}
