package sh.zolt.jarproof.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

/**
 * The {@code results} array of a SARIF run.
 *
 * <p>Every finding becomes one result carrying its rule id, its level, its summary as the message,
 * and one location. Jarproof's {@code info} severity maps to the SARIF level {@code note};
 * {@code warning} and {@code error} keep their names.
 *
 * <p>Three members carry the rest of what the finding knows, because a result holding only a summary
 * makes a code-scanning UI a worse reader of the analysis than the human report is. The message gains
 * a {@code markdown} rendering beside its bare {@code text} ({@link SarifMarkdown}); the evidence and
 * the remediation reach a consumer verbatim in a {@code properties} bag ({@link SarifProperties}); and
 * a finding whose evidence proved its referencing method executes gains a {@code codeFlows} entry
 * ({@link SarifCodeFlow}) so the proving chain is a path a reader can step through rather than a
 * sentence. All three are core SARIF 2.1.0, and all three are additions: what a consumer already read
 * -- the rule id, the level, the message text, and the location -- is written exactly as before.
 *
 * <p>A result is located in source only when the source file can be found. The candidate path is the
 * referencing class's package directory joined to the {@code SourceFile} attribute that class file
 * declared, and it counts only if one of the roots really holds that file -- the first root that does
 * wins. The reported URI is then the candidate itself: relative, slash-separated, and free of any
 * root, so nothing machine-specific reaches the document and two checkouts render the same bytes. The
 * line follows as a region only alongside a mapped path, because a line number attached to a JAR
 * points at nothing.
 *
 * <p>Every other case stays exactly the artifact-level result it was before source mapping existed:
 * no roots given, no source file in the class, or no root that holds the candidate. This is the whole
 * discipline of the feature -- a code-scanning annotation on a line nobody wrote is worse than no
 * annotation at all -- and it is why the file system is consulted here rather than trusted from a
 * flag.
 */
final class SarifResult {
    /** Member holding a human-readable string inside a message or description. */
    static final String TEXT = "text";

    /** Member holding the message an object shows, whether a result or one step of a code flow. */
    static final String MESSAGE = "message";

    /** Member holding a sequence of locations, whether a result's own or a thread flow's steps. */
    static final String LOCATIONS = "locations";

    private static final String RESULTS = "results";
    private static final String RULE_ID = "ruleId";
    private static final String LEVEL = "level";
    private static final String PHYSICAL_LOCATION = "physicalLocation";
    private static final String ARTIFACT_LOCATION = "artifactLocation";
    private static final String URI = "uri";
    private static final String REGION = "region";
    private static final String START_LINE = "startLine";
    private static final String NOTE = "note";

    private SarifResult() {
    }

    /**
     * Writes the {@code results} member of the run object already open.
     *
     * @param json the writer positioned inside the run object
     * @param findings the findings to render, in the order given
     * @param sourceRoots roots a source path may be resolved against, in the order the caller gave
     */
    static void appendResults(JsonText json, List<Finding> findings, List<Path> sourceRoots) {
        json.name(RESULTS).beginArray();
        for (Finding finding : findings) {
            appendResult(json, finding, sourceRoots);
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

    private static void appendResult(JsonText json, Finding finding, List<Path> sourceRoots) {
        json.beginObject();
        json.name(RULE_ID).value(finding.code().value());
        json.name(LEVEL).value(levelOf(finding.severity()));
        json.name(MESSAGE).beginObject();
        json.name(TEXT).value(finding.summary());
        SarifMarkdown.append(json, finding);
        json.endObject();
        appendLocation(json, finding.artifact(), mapped(finding.artifact(), sourceRoots));
        SarifCodeFlow.append(json, finding.evidence());
        SarifProperties.append(json, finding);
        json.endObject();
    }

    private static void appendLocation(JsonText json, ArtifactLocation location, Optional<String> source) {
        json.name(LOCATIONS).beginArray();
        json.beginObject();
        json.name(PHYSICAL_LOCATION).beginObject();
        json.name(ARTIFACT_LOCATION).beginObject();
        json.name(URI).value(source.orElseGet(location::artifact));
        json.endObject();
        if (source.isPresent()) {
            location.line().ifPresent(line -> appendRegion(json, line));
        }
        json.endObject();
        json.endObject();
        json.endArray();
    }

    private static void appendRegion(JsonText json, int line) {
        json.name(REGION).beginObject();
        json.name(START_LINE).value(line);
        json.endObject();
    }

    /** The source path of a finding, present only when one of the roots really holds that file. */
    private static Optional<String> mapped(ArtifactLocation location, List<Path> sourceRoots) {
        return candidate(location).filter(path -> sourceRoots.stream().anyMatch(root -> holds(root, path)));
    }

    /** Where the source file would sit under a root: the class's package directory, then its name. */
    private static Optional<String> candidate(ArtifactLocation location) {
        return location.classEntry()
                .flatMap(entry -> location.sourceFile().map(file -> packageDirectory(entry) + file));
    }

    /**
     * The directory part of a class entry, separator included, or nothing at all for a class in the
     * default package.
     */
    private static String packageDirectory(String classEntry) {
        int lastSeparator = classEntry.lastIndexOf('/');
        return lastSeparator < 0 ? "" : classEntry.substring(0, lastSeparator + 1);
    }

    /**
     * Whether a root really holds the candidate file.
     *
     * <p>A {@code SourceFile} attribute is whatever the class file says it is, so it can be text that
     * is not a path on this platform at all. That makes the finding unmappable, exactly like a root
     * that does not hold it, and never a refusal to report the run.
     */
    private static boolean holds(Path root, String candidate) {
        try {
            return Files.isRegularFile(root.resolve(candidate));
        } catch (IllegalArgumentException notAPath) {
            return false;
        }
    }
}
