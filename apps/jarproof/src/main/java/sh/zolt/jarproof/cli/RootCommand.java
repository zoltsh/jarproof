package sh.zolt.jarproof.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Root command; analysis commands land as complete vertical slices. */
@Command(
        name = "jarproof",
        description = "Find JAR hell before production.",
        mixinStandardHelpOptions = true,
        version = "jarproof 0.1.0-alpha.1-dev")
final class RootCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
