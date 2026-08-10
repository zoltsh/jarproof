package sh.zolt.jarproof.cli;

import java.io.PrintWriter;

/**
 * How every command reports a run it could not complete.
 *
 * <p>One line, on the diagnostic stream, carrying the explanation the engine or the flag validation
 * already produced -- no stack trace, no usage dump, and nothing on the findings stream, because a
 * consumer parsing that stream must never receive a failure where a report belongs.
 */
final class FailedInvocation {
    private FailedInvocation() {
    }

    /**
     * Reports a refused invocation.
     *
     * @param err the diagnostic stream the command was given
     * @param message the explanation to print
     * @return the process status an invocation error deserves
     */
    static int reported(PrintWriter err, String message) {
        err.println(message);
        err.flush();
        return ExitCode.INVOCATION.status();
    }
}
