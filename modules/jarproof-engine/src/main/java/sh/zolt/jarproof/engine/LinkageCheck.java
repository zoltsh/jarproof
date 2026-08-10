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
 * proof. Neither of those two modes claims reachability, and a class whose bytes no parser accepted is
 * skipped here because it already has its own diagnostic.
 *
 * <p>{@link Scope#REACHABLE} analyses every origin as well and then keeps only what {@link Reachability}
 * proves executable: a reference survives when the method it is written in is in the call graph, and it
 * is an error whatever its origin, because a proven chain from first-party code is the same problem in a
 * library as in the application. Each surviving finding also shows the chain that proved it, which is the
 * only thing this mode adds to a finding rather than removes from the report. The resolution and the
 * deduplication are identical in all three modes, so a finding cannot mean one thing at one scope and
 * something else at another, and a run at either quieter scope produces the evidence it always has
 * because there is no graph to quote. The reachability analysis reuses this check's own resolution table,
 * so the graph and the findings can never disagree about which declaration a reference means — nor, now,
 * about which method a chain arrives at.
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
    private final Optional<ReachableMethods> reachable;
    private final Map<String, Finding> found = new LinkedHashMap<>();

    private LinkageCheck(ArtifactCatalog catalog, JdkSymbolCatalog platform, Scope scope) {
        this.table = new ResolutionTable(catalog, platform);
        this.scope = scope;
        this.reachable = scope == Scope.REACHABLE
                ? Optional.of(Reachability.of(catalog, table))
                : Optional.empty();
    }

    /**
     * Runs the linkage checks.
     *
     * @param catalog the read classpath
     * @param platform the target runtime's symbol catalog, which answers before the classpath does
     * @param scope which bytecode origins to analyse, and how much proof a finding needs
     * @return one finding per distinct break, in observation order
     */
    static List<Finding> run(ArtifactCatalog catalog, JdkSymbolCatalog platform, Scope scope) {
        LinkageCheck check = new LinkageCheck(catalog, platform, scope);
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
                severity,
                reachable);
        declared.references().types().stream()
                .filter(type -> executable(declared, type.referencingMethod()))
                .forEach(type -> record(review.type(type)));
        declared.references().members().stream()
                .filter(member -> executable(declared, member.referencingMethod()))
                .forEach(member -> record(review.member(member)));
    }

    private void record(Optional<Finding> reviewed) {
        reviewed.ifPresent(finding -> found.putIfAbsent(key(finding), finding));
    }

    /**
     * Returns whether a reference is allowed to produce a finding at all.
     *
     * <p>Every reference qualifies unless the run asked for reachability, in which case the method the
     * reference is written in has to be in the call graph. Keeping the question here rather than inside
     * the review is what makes the two quiet modes byte-identical to a run before this mode existed:
     * with no graph, this always answers yes.
     */
    private boolean executable(IndexedClass declared, String referencingMethod) {
        return reachable.isEmpty()
                || reachable.get().reaches(declared.internalName(), referencingMethod);
    }

    private boolean analysed(ClasspathOrigin origin) {
        return scope != Scope.APPLICATION || origin == ClasspathOrigin.APPLICATION;
    }

    /**
     * Returns what an origin costs the reader. Library-only evidence is a warning because the JVM never
     * resolves what it never reaches — but under {@link Scope#REACHABLE} the reaching is exactly what
     * has been proved, so origin stops mattering and every surviving finding is an error.
     */
    private Severity severity(ClasspathOrigin origin) {
        boolean proven = scope == Scope.REACHABLE || origin == ClasspathOrigin.APPLICATION;
        return proven ? Severity.ERROR : Severity.WARNING;
    }

    private static String key(Finding finding) {
        return finding.code().value()
                + '\n' + finding.subject()
                + '\n' + finding.artifact().artifact()
                + '\n' + finding.artifact().classEntry().orElse("");
    }
}
