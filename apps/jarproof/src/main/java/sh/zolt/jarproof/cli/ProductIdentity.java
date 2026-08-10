package sh.zolt.jarproof.cli;

/**
 * The product's own names and versions, in one place.
 *
 * <p>Every surface that has to spell the tool out -- the command name, the {@code --version}
 * banner, the JSON envelope, the SARIF driver -- reads these constants instead of repeating the
 * text, so a rename or a release bump is a single edit. They are {@code static final} so that
 * annotation values can use them.
 */
final class ProductIdentity {
    /** The command users type, and the name reported in machine output. */
    static final String TOOL_NAME = "jarproof";

    /** The release this build reports. */
    static final String VERSION = "0.1.0-alpha.1-dev";

    /** What {@code --version} prints. A constant expression, so annotations can use it. */
    static final String VERSION_BANNER = TOOL_NAME + " " + VERSION;

    /** Version of the {@code check} JSON contract, bumped only by a breaking envelope change. */
    static final String JSON_VERSION = "1";

    private ProductIdentity() {
    }
}
