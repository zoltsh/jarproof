package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * One artifact's facts as JSON, for a script that would rather not parse a table.
 *
 * <p><strong>This shape is a versioned contract.</strong> {@code inspectJsonVersion} is the first
 * member of every document and carries the version of the envelope around it, exactly as the
 * {@code check} envelope carries its own. Naming the version is what makes the rest of the shape
 * promisable, so adding that member is the last breaking change this envelope gets.
 *
 * <p>Within a version, the member set and the member order written here are both fixed: a consumer
 * may read a member by name or rely on the position it arrives in. Optional members may join a
 * version, so a consumer must tolerate a member it does not recognise; only a change to a member it
 * already reads is a new version.
 *
 * <p>The canonical form every jarproof document shares holds here too: UTF-8 text, LF endings, and
 * the escaping rules in {@link JsonText}. Two runs over the same artifact produce the same bytes.
 */
final class ArtifactJson {
    private static final String INSPECT_JSON_VERSION = "inspectJsonVersion";
    private static final String VERSION = "1";
    private static final String ENTRY_COUNT = "entryCount";
    private static final String CLASS_COUNT = "classCount";
    private static final String NESTED_ARCHIVE_COUNT = "nestedArchiveCount";
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
        json.name(INSPECT_JSON_VERSION).value(VERSION);
        json.name(FindingJson.ARTIFACT).value(summary.artifact());
        json.name(ENTRY_COUNT).value(summary.entryCount());
        json.name(CLASS_COUNT).value(summary.classCount());
        json.name(NESTED_ARCHIVE_COUNT).value(summary.nestedArchiveCount());
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
