package sh.zolt.jarproof.cli;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import sh.zolt.jarproof.api.FindingCode;

/**
 * Prints the extended documentation for one diagnostic code, the way {@code rustc --explain} does.
 *
 * <p>A finding's own summary and remediation have to fit inside a report; this is where the
 * reasoning lives. Anything that is not a documented code -- misspelled, reserved, or from a newer
 * release -- fails with the invocation status and says which of those it was, rather than printing
 * nothing and claiming success.
 */
@Command(
        name = "explain",
        description = "Explain a diagnostic code: what it means, how the JVM gets there, how to fix it.",
        mixinStandardHelpOptions = true,
        version = ProductIdentity.VERSION_BANNER)
final class ExplainCommand implements Callable<Integer> {
    private static final String NOT_A_CODE = "Not a jarproof diagnostic code: ";
    private static final String FORMAT_HINT = "A code is JP followed by four digits, such as JP1003.";
    private static final String UNDOCUMENTED = "No explanation is available for ";
    private static final String RESERVED_HINT =
            "Codes stay reserved until their check ships; DESIGN.md lists the ranges.";

    @Parameters(index = "0", paramLabel = "CODE", description = "Diagnostic code such as JP1003.")
    private String code;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        Optional<FindingCode> parsed = parseCode();
        if (parsed.isEmpty()) {
            return failure(NOT_A_CODE + code, FORMAT_HINT);
        }
        Optional<String> text = ExplainText.of(parsed.get());
        if (text.isEmpty()) {
            return failure(UNDOCUMENTED + code, RESERVED_HINT);
        }
        PrintWriter out = spec.commandLine().getOut();
        out.print(text.get());
        out.flush();
        return ExitCode.CLEAN.status();
    }

    private Optional<FindingCode> parseCode() {
        try {
            return Optional.of(FindingCode.of(code));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private int failure(String message, String hint) {
        PrintWriter err = spec.commandLine().getErr();
        err.println(message);
        err.println(hint);
        err.flush();
        return ExitCode.INVOCATION.status();
    }
}
