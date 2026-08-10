package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.FindingCode;

/**
 * The extended documentation behind {@code jarproof explain}, and the index of it.
 *
 * <p>One UTF-8 text file per diagnostic code lives beside this class on the classpath, named after
 * the code. Keeping the prose in resources rather than in string literals lets it be as long and as
 * carefully worded as it needs to be, and lets a reviewer read a diff of the words alone.
 *
 * <p>A code with no file is not an error here: reserved codes and codes from a newer release both
 * legitimately have no text yet, so the caller decides what to say about that.
 *
 * <p>Which codes exist at all is answered by {@code codes.txt}, a resource holding one code per line
 * in code order, rather than by listing the folder: a native image resolves resources by declared
 * name and cannot enumerate a classpath directory, so a listing built by enumeration would work on
 * the JVM and silently come back empty from the binary. The index is therefore checked into the same
 * folder as the texts it names, and a build test keeps the two sets identical.
 */
final class ExplainText {
    private static final String FOLDER = "explain/";
    private static final String EXTENSION = ".txt";
    private static final String INDEX = "codes";
    private static final String COLUMN_GAP = "  ";
    private static final String NO_INDEX = "This build carries no index of documented diagnostic codes";

    private ExplainText() {
    }

    /**
     * Loads the documentation for one diagnostic code.
     *
     * @param code the code to explain
     * @return its text, or empty when this build documents no such code
     */
    static Optional<String> of(FindingCode code) {
        return read(code.value());
    }

    /**
     * Lists every code this build documents, in code order.
     *
     * @return the codes named by the index resource
     * @throws IllegalStateException when this build carries no index, which a native image missing
     *     the resource declaration would otherwise turn into an empty answer
     */
    static List<String> codes() {
        return read(INDEX).orElseThrow(() -> new IllegalStateException(NO_INDEX)).lines().toList();
    }

    /**
     * Renders the one-line-per-code index a reader gets for asking about the codes rather than one of
     * them: the code, two spaces, and the title its own documentation opens with.
     *
     * <p>The title line repeats the code it belongs to, so the code is printed once here and the rest
     * of that line follows it. A code the index names but this build holds no text for degrades to its
     * bare code rather than failing the listing; the test that keeps the index and the texts one set is
     * what stops that from reaching a release.
     *
     * @return the complete listing, one code per line, each line terminated by a single LF
     */
    static String listing() {
        StringBuilder listing = new StringBuilder();
        for (String code : codes()) {
            listing.append(code).append(COLUMN_GAP).append(title(code)).append('\n');
        }
        return listing.toString();
    }

    private static String title(String code) {
        return read(code)
                .flatMap(documentation -> documentation.lines().findFirst())
                .map(opening -> opening.substring(code.length()).strip())
                .orElse(code);
    }

    private static Optional<String> read(String name) {
        try (InputStream resource = ExplainText.class.getResourceAsStream(FOLDER + name + EXTENSION)) {
            if (resource == null) {
                return Optional.empty();
            }
            return Optional.of(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
