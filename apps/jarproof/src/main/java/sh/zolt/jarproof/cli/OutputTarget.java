package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Where a rendered report goes: the process output stream, or the file named by {@code --output}.
 *
 * <p>Both paths write the document exactly as rendered. Nothing re-encodes line endings and
 * nothing appends a separator, because the report already ends in a single LF and the whole point
 * of canonical output is that its bytes are the contract. Files are written as UTF-8 regardless of
 * the platform default.
 *
 * <p>A redirected write says so on the diagnostic stream, because the alternative is a command that
 * prints nothing at all and leaves a reader guessing whether it did the work. Writing to the stream
 * needs no such note: the document is the confirmation.
 */
final class OutputTarget {
    private static final String WROTE = "wrote ";
    private static final String TO = " to ";

    private final Optional<Path> file;
    private final PrintWriter stream;

    private OutputTarget(Optional<Path> file, PrintWriter stream) {
        this.file = file;
        this.stream = stream;
    }

    /**
     * Chooses where reports go. The file wins when {@code --output} named one.
     *
     * @param file the path named by {@code --output}, or empty
     * @param stream the writer the command was given
     * @return a target that writes to the file, or to the stream when there is none
     */
    static OutputTarget of(Optional<Path> file, PrintWriter stream) {
        return new OutputTarget(file, stream);
    }

    /**
     * Writes one rendered document.
     *
     * @param document the report, already terminated by a single LF
     * @throws IOException when the file cannot be written
     */
    void write(String document) throws IOException {
        if (file.isPresent()) {
            Files.writeString(file.get(), document, StandardCharsets.UTF_8);
            return;
        }
        stream.print(document);
        stream.flush();
    }

    /**
     * Notes a completed write that went to a file rather than to the stream.
     *
     * @param err the diagnostic stream the command was given
     * @param written what was written, named the way a reader would name it
     */
    void note(PrintWriter err, String written) {
        if (file.isEmpty()) {
            return;
        }
        err.println(WROTE + written + TO + file.get());
        err.flush();
    }
}
