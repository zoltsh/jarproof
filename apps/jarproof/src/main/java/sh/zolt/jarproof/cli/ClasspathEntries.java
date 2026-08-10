package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the {@code --classpath} values a caller typed into the entries the engine is handed.
 *
 * <p>Exactly one thing happens here: a value beginning with {@code @} names a UTF-8 file holding one
 * entry per line, and those lines take its place. Lines are trimmed and blank lines are skipped;
 * nothing else about a line is interpreted, so there are no comments, and a line that itself begins
 * with {@code @} is a path like any other rather than another list to read. That keeps the expansion
 * a single, explainable step instead of a small language.
 *
 * <p>Every surviving value becomes a path exactly as written. A wildcard, a directory, and a
 * relative path all travel through untouched, because the engine decides what they mean and because
 * the report has to be able to repeat the caller's own words back.
 */
final class ClasspathEntries {
    private static final String LIST_MARKER = "@";
    private static final String UNREADABLE_LIST = "This classpath list does not exist or cannot be read: ";

    private ClasspathEntries() {
    }

    /**
     * Expands the classpath values of one invocation.
     *
     * @param values the {@code --classpath} values in the order they were given
     * @return the classpath entries, in that same order, with every list expanded in place
     * @throws IllegalArgumentException when a named list cannot be read
     * @throws IOException when reading a list fails after it was found
     */
    static List<Path> of(List<String> values) throws IOException {
        List<Path> entries = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(LIST_MARKER)) {
                addList(entries, value.substring(LIST_MARKER.length()));
            } else {
                entries.add(Path.of(value));
            }
        }
        return List.copyOf(entries);
    }

    private static void addList(List<Path> entries, String name) throws IOException {
        Path list = Path.of(name);
        if (!Files.isReadable(list)) {
            throw new IllegalArgumentException(UNREADABLE_LIST + list);
        }
        for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
            String entry = line.trim();
            if (!entry.isEmpty()) {
                entries.add(Path.of(entry));
            }
        }
    }
}
