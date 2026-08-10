package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * One verification from end to end: assemble the classpath, read it, ask every check, order the answer.
 *
 * <p>The order of the phases is the whole design. The classpath is settled before anything is read,
 * because which copy of a class wins decides what the checks are even looking at. Everything is read
 * before any check runs, because most conflicts are only visible across artifacts. The findings are
 * counted against the budget before they are ordered, so an analysis that has clearly gone wrong fails
 * loudly instead of returning a report nobody can act on.
 */
final class VerificationRun {
    private VerificationRun() {
    }

    /**
     * Runs one verification.
     *
     * @param request the validated request
     * @return the canonically ordered result
     */
    static VerificationResult execute(VerificationRequest request) {
        ResourceBudget budget = new ResourceBudget();
        ArtifactCatalog catalog = ArtifactCatalog.read(request, budget);
        List<Finding> findings = new ArrayList<>(catalog.findings());
        findings.addAll(DuplicateClassCheck.run(catalog));
        findings.addAll(SplitPackageCheck.run(catalog));
        findings.addAll(ArtifactVersionCheck.run(catalog));
        findings.addAll(SealedPackageCheck.run(catalog));
        findings.addAll(ClassFileVersionCheck.run(catalog, request.targetRuntime()));
        findings.addAll(BytecodeLevelCheck.run(catalog));
        budget.checkFindingCount(findings.size());
        findings.sort(FindingOrder.CANONICAL);
        return new VerificationResult(findings);
    }
}
