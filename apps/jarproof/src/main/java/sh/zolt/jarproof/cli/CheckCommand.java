package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
 * new one cannot be hidden. Invalid input and a breached engine ceiling both end the run with one
 * explanatory line and the invocation status, because a partial report of a run that could not be
 * completed is worse than a clear refusal.
 */
@Command(
        name = "check",
        description = "Verify an application against its runtime classpath.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER)
final class CheckCommand implements Callable<Integer> {
    @Mixin
    private RequestOptions options;

    @Option(names = "--baseline", description = "Suppress the findings this file already accepts.")
    private Path baseline;

    @Option(
            names = "--fail-on",
            description = "Lowest severity that fails the run: error|warning|never. Defaults to error.")
    private FailureThreshold failOn = FailureThreshold.ERROR;

    @Option(names = FlagName.FORMAT, description = "Report format: human|json|sarif. Defaults to human.")
    private ReportFormat format = ReportFormat.HUMAN;

    @Option(names = FlagName.OUTPUT, description = "Write the report to this file instead of stdout.")
    private Path output;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            return check().status();
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException refused) {
            return FailedInvocation.reported(spec.commandLine().getErr(), refused.getMessage());
        } catch (IOException unwritable) {
            return FailedInvocation.reported(spec.commandLine().getErr(), unwritable.toString());
        }
    }

    private ExitCode check() throws IOException {
        VerificationRequest request = options.request();
        VerificationResult result = Jarproof.verify(request);
        VerificationResult reported = accepted(request, result);
        VerificationResult rendered =
                format.rootsArtifactPaths() ? options.pathRoot().rewrite(reported) : reported;
        OutputTarget.of(Optional.ofNullable(output), spec.commandLine().getOut())
                .write(format.render(request, rendered));
        return failOn.verdict(reported);
    }

    private VerificationResult accepted(VerificationRequest request, VerificationResult result) throws IOException {
        if (baseline == null) {
            return result;
        }
        return AcceptedBaseline.read(baseline)
                .applyTo(request, options.profile(), result, spec.commandLine().getErr());
    }
}
