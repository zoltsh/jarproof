package sh.zolt.jarproof.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Root command; analysis commands land as complete vertical slices. */
@Command(
        name = ProductIdentity.TOOL_NAME,
        description = "Find JAR hell before production.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER,
        subcommands = ExplainCommand.class)
final class RootCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
