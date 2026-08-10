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
import sh.zolt.jarproof.api.ArtifactSummary;
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
 *
 * <p>The JSON form is a versioned contract of its own, kept by {@link ArtifactJson}: a script may
 * gate on it exactly as it gates on the {@code check} envelope. Its {@code artifact} member is
 * measured from {@code --path-root} for the same reason the {@code check} envelope's is -- a machine
 * consumer needs a path that does not move with the checkout directory -- while the table a person
 * reads keeps the spelling they typed.
 */
@Command(
        name = "inspect",
        description = "Report one artifact's layout facts. The JSON is a versioned contract.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER)
final class InspectCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "ARTIFACT", description = "JAR or class directory to read.")
    private Path artifact;

    @Option(names = FlagName.FORMAT, description = "Fact format: human|json. Defaults to human.")
    private InspectFormat format = InspectFormat.HUMAN;

    @Option(names = FlagName.OUTPUT, description = "Write the facts to this file instead of stdout.")
    private Path output;

    @Option(
            names = FlagName.PATH_ROOT,
            description = "Root the JSON form measures the artifact path from. Defaults to the working"
                    + " directory, and the table always shows the path as it was given.")
    private Path pathRoot = Path.of("");

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            return inspect().status();
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException refused) {
            return FailedInvocation.reported(spec.commandLine().getErr(), refused.getMessage());
        } catch (IOException unwritable) {
            return FailedInvocation.unwritable(spec.commandLine().getErr(), unwritable);
        }
    }

    private ExitCode inspect() throws IOException {
        PathRoot root = PathRoot.of(pathRoot);
        ArtifactSummary read = Jarproof.inspect(artifact);
        String facts = format.render(format.rootsArtifactPaths() ? root.rewrite(read) : read);
        OutputTarget target = OutputTarget.of(Optional.ofNullable(output), spec.commandLine().getOut());
        target.write(facts);
        target.note(spec.commandLine().getErr(), "the inspection");
        return ExitCode.CLEAN;
    }
}
