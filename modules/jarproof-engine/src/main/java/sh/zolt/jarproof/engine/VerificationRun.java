package sh.zolt.jarproof.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * One verification from end to end: settle the platform symbols, assemble the classpath, read it, ask
 * every check, order the answer.
 *
 * <p>The order of the phases is the whole design. The classpath is settled before anything is read,
 * because which copy of a class wins decides what the checks are even looking at. Everything is read
 * before any check runs, because most conflicts are only visible across artifacts. The findings are
 * counted against the budget before they are ordered, so an analysis that has clearly gone wrong fails
 * loudly instead of returning a report nobody can act on.
 *
 * <p>Platform symbols come from the bundled data for the target release unless the request names a JDK
 * installation, in which case that JDK's own signature archive answers instead. The split between the
 * handle that archive is opened through and the text it is reported by enters here rather than deeper
 * down, because this is the one place a caller's {@code jdkHome} becomes a file name: the archive path is
 * settled once into an absolute normalized handle, exactly as a classpath entry is, and the caller's own
 * text travels beside it so {@link JdkSymbolCatalog#fromCtSym} never has to resolve or render anything
 * itself. A signature archive that carries nothing at all for the target release and one that carries
 * entries but not the root class mean the same thing to a caller — this JDK cannot describe that release —
 * so both are reported with the same explanation, and the archive's own words are kept as the cause.
 */
final class VerificationRun {
    /**
     * The class every platform release declares. It is named from the compiler's own view of
     * {@link Object} so this member spells the internal name of a platform class in exactly one place.
     */
    private static final String ROOT_CLASS = ServiceBinaryName.internalName(Object.class.getName());

    private static final String SIGNATURE_DIRECTORY = "lib";
    private static final String SIGNATURE_FILE = "ct.sym";
    private static final String NO_SIGNATURE_ARCHIVE = "This JDK has no signature archive at ";
    private static final String NO_ROOT_CLASS = "This signature archive declares no ";
    private static final String FOR_RELEASE = " for Java ";
    private static final String OWN_RELEASE = ". A JDK's signature archive never describes its own"
            + " release, so --jdk needs a JDK newer than the target Java release.";

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
        JdkSymbolCatalog platform = platformSymbols(request);
        ArtifactCatalog catalog = ArtifactCatalog.read(request, budget);
        List<Finding> findings = new ArrayList<>(catalog.findings());
        findings.addAll(DuplicateClassCheck.run(catalog));
        findings.addAll(SplitPackageCheck.run(catalog));
        findings.addAll(ArtifactVersionCheck.run(catalog));
        findings.addAll(SealedPackageCheck.run(catalog));
        findings.addAll(ClassFileVersionCheck.run(catalog, request.targetRuntime()));
        findings.addAll(BytecodeLevelCheck.run(catalog));
        findings.addAll(LinkageCheck.run(catalog, platform, request.scope()));
        findings.addAll(ServiceProviderCheck.run(catalog, platform, budget));
        findings.addAll(ModuleCheck.run(catalog, platform));
        budget.checkFindingCount(findings.size());
        findings.sort(FindingOrder.CANONICAL);
        return new VerificationResult(findings);
    }

    private static JdkSymbolCatalog platformSymbols(VerificationRequest request) {
        int javaRelease = request.targetRuntime().javaRelease();
        Optional<Path> home = request.jdkHome();
        if (home.isEmpty()) {
            return JdkSymbolCatalog.forRelease(javaRelease);
        }
        Path signatures = home.get().resolve(SIGNATURE_DIRECTORY).resolve(SIGNATURE_FILE);
        Path handle = signatures.toAbsolutePath().normalize();
        if (!Files.isReadable(handle)) {
            throw new IllegalArgumentException(NO_SIGNATURE_ARCHIVE + signatures);
        }
        return describing(handle, signatures.toString(), javaRelease);
    }

    private static JdkSymbolCatalog describing(Path handle, String display, int javaRelease) {
        JdkSymbolCatalog catalog = supplied(handle, display, javaRelease);
        if (catalog.classShape(ROOT_CLASS).isEmpty()) {
            throw new IllegalArgumentException(
                    NO_ROOT_CLASS + ROOT_CLASS + FOR_RELEASE + javaRelease + OWN_RELEASE);
        }
        return catalog;
    }

    private static JdkSymbolCatalog supplied(Path handle, String display, int javaRelease) {
        try {
            return JdkSymbolCatalog.fromCtSym(handle, display, javaRelease);
        } catch (IllegalArgumentException undescribed) {
            throw new IllegalArgumentException(undescribed.getMessage() + OWN_RELEASE, undescribed);
        }
    }
}
