package sh.zolt.jarproof.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Root command; the four things jarproof does, in the order a user meets them.
 *
 * <p>{@code check} is the product. {@code baseline} is how a project with existing findings starts
 * using it. {@code inspect} answers what is in one artifact, and {@code explain} answers what a
 * diagnostic code means. Running jarproof with no command prints this list rather than guessing.
 */
@Command(
        name = ProductIdentity.TOOL_NAME,
        description = "Find JAR hell before production.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER,
        subcommands = {
            CheckCommand.class,
            BaselineCommand.class,
            InspectCommand.class,
            ExplainCommand.class
        })
final class RootCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
