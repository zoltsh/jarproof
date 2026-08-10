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
 */
final class OutputTarget {
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
}
