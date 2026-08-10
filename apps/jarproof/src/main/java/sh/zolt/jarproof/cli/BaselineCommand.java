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
 * Records every finding of a run as accepted, so a project can start gating on what is new.
 *
 * <p>A brownfield application with a hundred existing findings cannot fix them all before its first
 * gated build. Recording them once turns that hundred into the floor and lets the next new finding
 * fail the build on the day it appears.
 *
 * <p>Recording is not a verdict, so a successful recording is a clean status however many findings it
 * accepted. The file it writes also carries what the acceptance was measured against -- the target
 * release, the preview policy, the scope, and the runtime symbol profile -- because the same
 * fingerprints judged against a different runtime are a different judgement.
 */
@Command(
        name = "baseline",
        description = "Record the current findings as accepted, so later runs report only new ones.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER)
final class BaselineCommand implements Callable<Integer> {
    @Mixin
    private RequestOptions options;

    @Option(names = "--out", required = true, description = "File the accepted findings are written to.")
    private Path out;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            return record().status();
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException refused) {
            return FailedInvocation.reported(spec.commandLine().getErr(), refused.getMessage());
        } catch (IOException unwritable) {
            return FailedInvocation.reported(spec.commandLine().getErr(), unwritable.toString());
        }
    }

    private ExitCode record() throws IOException {
        VerificationRequest request = options.request();
        VerificationResult result = Jarproof.verify(request);
        BaselineDocument recorded = BaselineDocument.of(request, options.profile(), result);
        OutputTarget.of(Optional.of(out), spec.commandLine().getOut()).write(BaselineJson.write(recorded));
        return ExitCode.CLEAN;
    }
}
