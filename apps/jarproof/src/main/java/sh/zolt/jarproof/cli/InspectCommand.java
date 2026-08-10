package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import sh.zolt.jarproof.engine.Jarproof;

/**
 * Reports the facts one artifact presents on its own.
 *
 * <p>This is the question asked before any verification: what is actually in this JAR. It compares
 * nothing, so it has nothing to fail on -- a readable artifact is a clean status whatever it turns
 * out to contain, and only an unreadable one is an error.
 *
 * <p>The facts are raw. A multi-release archive reports every version directory it carries rather
 * than the one a chosen runtime would use, because selecting for a release is what {@code check}
 * does and reporting the selection here would answer a question nobody asked.
 */
@Command(
        name = "inspect",
        description = "Report one artifact's layout facts. The JSON shape is unstable in 0.1.x.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER)
final class InspectCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "ARTIFACT", description = "JAR or class directory to read.")
    private Path artifact;

    @Option(names = FlagName.FORMAT, description = "Fact format: human|json. Defaults to human.")
    private InspectFormat format = InspectFormat.HUMAN;

    @Option(names = FlagName.OUTPUT, description = "Write the facts to this file instead of stdout.")
    private Path output;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            return inspect().status();
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException refused) {
            return FailedInvocation.reported(spec.commandLine().getErr(), refused.getMessage());
        } catch (IOException unwritable) {
            return FailedInvocation.reported(spec.commandLine().getErr(), unwritable.toString());
        }
    }

    private ExitCode inspect() throws IOException {
        String facts = format.render(Jarproof.inspect(artifact));
        OutputTarget.of(Optional.ofNullable(output), spec.commandLine().getOut()).write(facts);
        return ExitCode.CLEAN;
    }
}
