package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Reports an application root that presents no class files at all.
 *
 * <p>Every other check answers a question about bytecode. This one answers the question a caller
 * never thinks to ask: was there any bytecode. A root naming a directory the build has not written
 * yet, a module whose output moved, or an archive holding only resources all read successfully and
 * contribute nothing, and a run whose only application root is one of those reports no findings --
 * which reads as proof the application links cleanly rather than as proof nothing was looked at.
 *
 * <p>Informational, because an empty root is legal and occasionally deliberate: a run may name
 * several roots and mean for one of them to be empty. The finding is per root rather than per run,
 * so a report says which one, and a run whose other roots carry classes still says so about the one
 * that does not.
 *
 * <p>Only application roots are asked. A classpath entry contributing no classes is ordinary -- a
 * resource bundle, a licence jar, a directory of configuration -- and the runtime is entirely happy
 * with it, so saying anything about it would be noise.
 *
 * <p>A root that reading already said something about is not reported again either. An archive whose
 * only class file is unparseable contributes no classes and produces JP3004, so the report is not
 * silent about it -- and silence is the whole thing this check exists to prevent. Saying both would
 * be two findings for one problem, with the second one adding nothing to the first.
 */
final class EmptyRootCheck {
    private static final FindingCode CODE = FindingCode.of("JP3007");

    private EmptyRootCheck() {
    }

    /**
     * Runs the empty-application-root check.
     *
     * @param catalog the read classpath
     * @return one finding per application root that contributes no classes
     */
    static List<Finding> run(ArtifactCatalog catalog) {
        List<Finding> findings = new ArrayList<>();
        for (IndexedArtifact artifact : catalog.artifacts()) {
            if (contributesNothing(artifact)) {
                findings.add(finding(artifact.entry().display()));
            }
        }
        return List.copyOf(findings);
    }

    private static boolean contributesNothing(IndexedArtifact artifact) {
        return artifact.entry().origin() == ClasspathOrigin.APPLICATION
                && artifact.classes().isEmpty()
                && artifact.findings().isEmpty();
    }

    private static Finding finding(String display) {
        return new Finding(
                CODE,
                Severity.INFO,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(display),
                display,
                "application root contributes no classes",
                "This application root was read and holds no class files, so no reference of yours was"
                        + " examined through it. A clean report for a run like this says nothing about the"
                        + " application: it says the analysis found nothing to analyse.",
                List.of(new Evidence("the root presents no class file to the target runtime")),
                List.of(new Remediation("Name the directory or archive that holds the compiled classes,"
                        + " build it first, or drop the root from the run.")));
    }
}
