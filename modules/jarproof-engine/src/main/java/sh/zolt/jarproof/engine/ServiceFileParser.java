package sh.zolt.jarproof.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one service configuration file the way the runtime reads it.
 *
 * <p>The bytes are UTF-8 text. A number sign begins a comment that runs to the end of its line,
 * whitespace around what is left is insignificant, and a line holding nothing after both rules
 * applied is skipped. Every physical line is counted, comments and blank lines included, so a
 * reported line number is the line a reader would scroll to. A line terminator may be a line feed,
 * a carriage return, or the pair, because a resource written on one platform is read on every other.
 *
 * <p>Bytes that are not valid UTF-8 decode to the replacement character, which is not an identifier
 * character, so undecodable text arrives at the caller as a line that fails the name grammar rather
 * than as a parse failure that hides the rest of the file.
 */
final class ServiceFileParser {
    private static final char COMMENT_START = '#';

    private ServiceFileParser() {
    }

    /**
     * Parses one service configuration file.
     *
     * @param content the complete file bytes
     * @return the surviving provider lines, in file order
     */
    static List<ServiceProviderEntry> parse(byte[] content) {
        List<ServiceProviderEntry> entries = new ArrayList<>();
        int lineNumber = 0;
        for (String line : new String(content, StandardCharsets.UTF_8).lines().toList()) {
            lineNumber++;
            String declared = withoutComment(line);
            if (!declared.isEmpty()) {
                entries.add(new ServiceProviderEntry(declared, lineNumber));
            }
        }
        return List.copyOf(entries);
    }

    private static String withoutComment(String line) {
        int comment = line.indexOf(COMMENT_START);
        return (comment < 0 ? line : line.substring(0, comment)).strip();
    }
}
