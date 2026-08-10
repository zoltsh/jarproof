package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

/**
 * How every command reports a run it could not complete.
 *
 * <p>One line, on the diagnostic stream, carrying the explanation the engine or the flag validation
 * already produced -- no stack trace, no usage dump, and nothing on the findings stream, because a
 * consumer parsing that stream must never receive a failure where a report belongs.
 *
 * <p>A file that cannot be written is the one failure the runtime describes in its own vocabulary
 * rather than the user's: {@code java.nio.file.NoSuchFileException: build/out/report.json} names a
 * Java class where a reader needs a reason. Every command that writes a file therefore reports it
 * through here, in one sentence saying what could not be written, where, and why.
 */
final class FailedInvocation {
    private static final String CANNOT_WRITE = "Cannot write the report to ";
    private static final String BECAUSE = ": ";
    private static final String NO_DIRECTORY = "the directory does not exist";
    private static final String NO_PERMISSION = "permission was denied";

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

    /**
     * Reports a document that could not be written.
     *
     * @param err the diagnostic stream the command was given
     * @param failure what the file system refused
     * @return the process status an invocation error deserves
     */
    static int unwritable(PrintWriter err, IOException failure) {
        if (failure instanceof FileSystemException named) {
            return reported(err, CANNOT_WRITE + named.getFile() + BECAUSE + reasonOf(named));
        }
        return reported(err, failure.getMessage());
    }

    /** The one clause that says why, for the two refusals a caller can act on. */
    private static String reasonOf(FileSystemException failure) {
        if (failure instanceof NoSuchFileException) {
            return NO_DIRECTORY;
        }
        return failure instanceof AccessDeniedException ? NO_PERMISSION : failure.getMessage();
    }
}
