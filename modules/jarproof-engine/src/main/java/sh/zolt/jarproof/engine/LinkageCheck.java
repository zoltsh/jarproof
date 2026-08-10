package sh.zolt.jarproof.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;

/**
 * Resolves every reference the analysed bytecode triggers, and reports the ones that will not link.
 *
 * <p><strong>Which bytecode is analysed.</strong> Scope decides it, and DESIGN §4 decides scope. Under
 * {@link Scope#APPLICATION} only classes from application roots are analysed at all, and every finding
 * is an error, because a broken reference in first-party code is a fact about the artifact under test.
 * Under {@link Scope#ALL} every origin is analysed, and a finding whose referencing class came from a
 * classpath entry is a warning: mature libraries reference optional integrations they do not ship, and
 * the JVM never resolves what is never reached, so library-to-library evidence is real without being
 * proof. Neither mode claims reachability, and a class whose bytes no parser accepted is skipped here
 * because it already has its own diagnostic.
 *
 * <p><strong>What one finding covers.</strong> Findings are deduplicated by code, subject, and
 * referencing class, and the first reference to observe a break supplies its evidence and its source
 * line. Two hundred call sites reaching the same absent method in one class are one problem, and
 * reporting them two hundred times would bury the other nineteen problems in the report.
 *
 * <p>Findings come back in the order they were observed. Ordering them is the pipeline's job, so this
 * check never sorts and never has to agree with anything about how.
 */
final class LinkageCheck {
    private final ResolutionTable table;
    private final Scope scope;
    private final Map<String, Finding> found = new LinkedHashMap<>();

    private LinkageCheck(ResolutionTable table, Scope scope) {
        this.table = table;
        this.scope = scope;
    }

    /**
     * Runs the linkage checks.
     *
     * @param catalog the read classpath
     * @param platform the target runtime's symbol catalog, which answers before the classpath does
     * @param scope which bytecode origins to analyse
     * @return one finding per distinct break, in observation order
     */
    static List<Finding> run(ArtifactCatalog catalog, JdkSymbolCatalog platform, Scope scope) {
        LinkageCheck check = new LinkageCheck(new ResolutionTable(catalog, platform), scope);
        for (IndexedArtifact artifact : catalog.artifacts()) {
            if (check.analysed(artifact.entry().origin())) {
                check.artifact(artifact);
            }
        }
        return List.copyOf(check.found.values());
    }

    private void artifact(IndexedArtifact artifact) {
        Severity severity = severity(artifact.entry().origin());
        for (IndexedClass declared : artifact.classes()) {
            declared.shape().ifPresent(shape -> review(artifact, declared, shape, severity));
        }
    }

    private void review(IndexedArtifact artifact, IndexedClass declared, ClassShape shape, Severity severity) {
        LinkageReview review = new LinkageReview(
                table,
                ResolvedClass.ofClasspath(
                        declared.internalName(), shape, new ClassDeclaration(artifact.entry(), declared)),
                ArtifactLocation.ofSource(
                        artifact.entry().display(),
                        declared.entryName(),
                        declared.sourceFile(),
                        Optional.empty()),
                severity);
        declared.references().types().forEach(type -> record(review.type(type)));
        declared.references().members().forEach(member -> record(review.member(member)));
    }

    private void record(Optional<Finding> reviewed) {
        reviewed.ifPresent(finding -> found.putIfAbsent(key(finding), finding));
    }

    private boolean analysed(ClasspathOrigin origin) {
        return scope == Scope.ALL || origin == ClasspathOrigin.APPLICATION;
    }

    private static Severity severity(ClasspathOrigin origin) {
        return origin == ClasspathOrigin.APPLICATION ? Severity.ERROR : Severity.WARNING;
    }

    private static String key(Finding finding) {
        return finding.code().value()
                + '\n' + finding.subject()
                + '\n' + finding.artifact().artifact()
                + '\n' + finding.artifact().classEntry().orElse("");
    }
}
