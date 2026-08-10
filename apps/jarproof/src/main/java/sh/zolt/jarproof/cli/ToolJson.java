package sh.zolt.jarproof.cli;

/**
 * The tool-identification block shared by every machine format.
 *
 * <p>The JSON envelope carries it as {@code tool}, and SARIF carries the same two members inside
 * {@code tool.driver}. Both read the member names from here so the spelling exists once.
 */
final class ToolJson {
    /** Member holding the producing tool. */
    static final String TOOL = "tool";

    /** Member holding a name. */
    static final String NAME = "name";

    /** Member holding a version. */
    static final String VERSION = "version";

    private ToolJson() {
    }

    /**
     * Writes the tool's name and version as two members of the object already open.
     *
     * @param json the writer positioned inside an object
     */
    static void appendIdentity(JsonText json) {
        json.name(NAME).value(ProductIdentity.TOOL_NAME);
        json.name(VERSION).value(ProductIdentity.VERSION);
    }
}
