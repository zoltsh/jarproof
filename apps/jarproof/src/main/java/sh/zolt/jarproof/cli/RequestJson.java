package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;

/**
 * The three request facts that decide whether two runs are comparable at all.
 *
 * <p>The JSON envelope reports them under {@code request} so a reader knows what the findings were
 * measured against, and a baseline records the same three so a stale baseline is recognised rather
 * than silently applied to a different target. Both spell the member names from here.
 */
final class RequestJson {
    /** Member holding the target Java release. */
    static final String TARGET_JAVA = "targetJava";

    /** Member holding the preview-feature policy. */
    static final String PREVIEW = "preview";

    /** Member holding the analysis scope. */
    static final String SCOPE = "scope";

    private RequestJson() {
    }

    /**
     * Writes the target release, preview policy, and scope as members of the object already open.
     *
     * @param json the writer positioned inside an object
     * @param javaRelease the verified Java release
     * @param preview the preview-feature policy
     * @param scope the analysis scope
     */
    static void appendFields(JsonText json, int javaRelease, PreviewMode preview, Scope scope) {
        json.name(TARGET_JAVA).value(javaRelease);
        json.name(PREVIEW).value(CanonicalName.of(preview));
        json.name(SCOPE).value(CanonicalName.of(scope));
    }
}
