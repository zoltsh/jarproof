package sh.zolt.jarproof.cli;

/**
 * The command lines this harness spells.
 *
 * <p>Every assertion here is written against the command line a user types, so the spellings live in
 * one place for the same reason the CLI keeps its shared flag names in one place: a harness that
 * quietly drifted to a flag the product does not have would still compile, still pass, and prove
 * nothing.
 */
final class CorpusCommand {
    /** Verifies an application against the classpath it will be launched with. */
    static final String CHECK = "check";

    /** Records the findings a run produced instead of reporting them. */
    static final String BASELINE = "baseline";

    /** Names an artifact whose own bytecode may raise linkage errors. */
    static final String APPLICATION = "--application";

    /** Names one entry of the runtime classpath the application is verified against. */
    static final String CLASSPATH = "--classpath";

    /** Names the Java release the classpath is expected to run on. */
    static final String TARGET_JAVA = "--target-java";

    /** The release the whole corpus is measured against, which the bundled profile describes. */
    static final String JAVA_17 = "17";

    /** Chooses the report format. */
    static final String FORMAT = "--format";

    /** The machine format every assertion about findings reads. */
    static final String JSON = "json";

    /** Redirects the report to a file. */
    static final String OUTPUT = "--output";

    /** Roots the artifact paths machine output prints, so a report is machine-independent. */
    static final String PATH_ROOT = "--path-root";

    /** Chooses which bytecode origins may produce linkage errors. */
    static final String SCOPE = "--scope";

    /** Inspects every origin rather than application bytecode alone. */
    static final String ALL = "all";

    /** Reports linkage errors only for references originating in application bytecode. */
    static final String APPLICATION_ONLY = "application";

    /** Suppresses the findings a recorded baseline already accepts. */
    static final String BASELINE_FLAG = "--baseline";

    /** Names the file {@code baseline} records into. */
    static final String OUT = "--out";

    private CorpusCommand() {
    }
}
