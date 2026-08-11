package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;
import sh.zolt.jarproof.engine.Jarproof;

/**
 * Verifies an application against the classpath it will be launched with.
 *
 * <p>This is the command a release gate runs, so the two streams are kept strictly apart: findings
 * go to the output stream or to the file named by {@code --output}, and everything else -- baseline
 * notes, warnings, failures -- goes to the diagnostic stream. A pipeline can therefore consume the
 * report without filtering, and a person still sees why the numbers are what they are.
 *
 * <p>The order of the work is deliberate. The engine answers first, a baseline is applied second, and
 * the process status is decided from what survived, so an accepted finding cannot fail a build and a
 * new one cannot be hidden. {@code --prune-stale} then rewrites that baseline from the comparison
 * already made, which is why pruning cannot move the verdict: the report and the status are read from
 * what the comparison said, not from what the file ended up holding. Invalid input and a breached
 * engine ceiling both end the run with one explanatory line and the invocation status, because a
 * partial report of a run that could not be completed is worse than a clear refusal.
 */
@Command(
        name = "check",
        description = "Verify an application against its runtime classpath.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER)
final class CheckCommand implements Callable<Integer> {
    private static final String BASELINE = "--baseline";
    private static final String PRUNE_STALE = "--prune-stale";
    private static final String NEEDS_A_BASELINE = " has nothing to prune without ";
    private static final String REPORT = " report";

    @Mixin
    private RequestOptions options;

    @Option(names = BASELINE, description = "Suppress the findings this file already accepts.")
    private Path baseline;

    @Option(
            names = PRUNE_STALE,
            description = "Rewrite the file named by " + BASELINE + " to hold only the entries this run"
                    + " still observed, keeping everything it records about what was accepted."
                    + " A run with nothing stale writes nothing, so the file changes only when the"
                    + " baseline really ratcheted down.")
    private boolean pruneStale;

    @Option(
            names = "--fail-on",
            description = "Lowest severity that fails the run: error|warning|never. Defaults to error.")
    private FailureThreshold failOn = FailureThreshold.ERROR;

    @Option(names = FlagName.FORMAT, description = "Report format: human|json|sarif. Defaults to human.")
    private ReportFormat format = ReportFormat.HUMAN;

    @Option(
            names = FlagName.OUTPUT,
            description = "Write the report to this file instead of stdout. The run then confirms the"
                    + " write on the diagnostic stream, so a redirected report is never silent.")
    private Path output;

    @Option(
            names = "--source-root",
            description = "Root a SARIF result's source path is resolved against. Repeatable, and the"
                    + " first root that really holds the file wins. A finding whose source file no root"
                    + " holds stays an artifact-level result, and no other format reads these roots.")
    private List<Path> sourceRoots = List.of();

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            return check().status();
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException refused) {
            return FailedInvocation.reported(spec.commandLine().getErr(), refused.getMessage());
        } catch (IOException unwritable) {
            return FailedInvocation.unwritable(spec.commandLine().getErr(), unwritable);
        }
    }

    private ExitCode check() throws IOException {
        refuseAPruneWithNothingToPrune();
        PathRoot root = options.pathRoot();
        VerificationRequest request = options.request();
        VerificationResult result = Jarproof.verify(request);
        VerificationResult reported = accepted(request, root, result);
        VerificationResult rendered =
                format.rootsArtifactPaths() ? root.rewrite(request, reported) : reported;
        OutputTarget target = OutputTarget.of(Optional.ofNullable(output), spec.commandLine().getOut());
        target.write(report(request, rendered));
        target.note(spec.commandLine().getErr(), CanonicalName.of(format) + REPORT);
        return failOn.verdict(reported);
    }

    /**
     * Renders the report in the requested format.
     *
     * <p>SARIF is handed the source roots directly because it is the only format that resolves them:
     * a root is a render-time input read from the file system, not a fact the analysis produced, so it
     * has no place in the vocabulary every format shares. Passing none is the same as having none,
     * which is what every other format does with them.
     *
     * @param request the request the run answered
     * @param rendered the findings as they will be reported
     * @return the complete report text
     */
    private String report(VerificationRequest request, VerificationResult rendered) {
        return format.render(request, rendered, sourceRoots);
    }

    /** Refuses a prune that has no file to rewrite, before any analysis is done for it. */
    private void refuseAPruneWithNothingToPrune() {
        if (pruneStale && baseline == null) {
            throw new IllegalArgumentException(PRUNE_STALE + NEEDS_A_BASELINE + BASELINE);
        }
    }

    /**
     * Applies the baseline, keeping only the findings it does not already accept.
     *
     * <p>What the run examined survives the filter untouched. A baseline decides which findings are
     * news, not how much was analysed to find them, so a run whose every finding was accepted still
     * reports the classes and artifacts it read -- which is the whole difference between a ratcheted
     * clean report and a run that checked nothing.
     */
    private VerificationResult accepted(VerificationRequest request, PathRoot root, VerificationResult result)
            throws IOException {
        if (baseline == null) {
            return result;
        }
        PrintWriter err = spec.commandLine().getErr();
        AcceptedBaseline accepted = AcceptedBaseline.read(baseline, root);
        BaselineComparison comparison = accepted.applyTo(request, options.profile(), result, err);
        if (pruneStale) {
            accepted.pruneStale(comparison, err);
        }
        return new VerificationResult(
                comparison.newFindings(), result.analyzedClassCount(), result.analyzedArtifactCount());
    }
}
