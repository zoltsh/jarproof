package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import sh.zolt.jarproof.api.FindingCode;

/**
 * The extended documentation behind {@code jarproof explain}.
 *
 * <p>One UTF-8 text file per diagnostic code lives beside this class on the classpath, named after
 * the code. Keeping the prose in resources rather than in string literals lets it be as long and as
 * carefully worded as it needs to be, and lets a reviewer read a diff of the words alone.
 *
 * <p>A code with no file is not an error here: reserved codes and codes from a newer release both
 * legitimately have no text yet, so the caller decides what to say about that.
 */
final class ExplainText {
    private static final String FOLDER = "explain/";
    private static final String EXTENSION = ".txt";

    private ExplainText() {
    }

    /**
     * Loads the documentation for one diagnostic code.
     *
     * @param code the code to explain
     * @return its text, or empty when this build documents no such code
     */
    static Optional<String> of(FindingCode code) {
        try (InputStream resource = ExplainText.class.getResourceAsStream(FOLDER + code.value() + EXTENSION)) {
            if (resource == null) {
                return Optional.empty();
            }
            return Optional.of(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
