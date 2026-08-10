package sh.zolt.jarproof.cli;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.VerificationRequest;

/**
 * The path spellings machine output uses inside the prose the engine composed.
 *
 * <p>An evidence line quotes the caller's own path text -- {@code selected /home/ci/build/lib.jar
 * (9f79b9e2)} -- which is exactly right for a person and wrong for a machine format whose bytes are
 * a determinism contract: the artifact field of the same finding is measured from
 * {@code --path-root} while the sentence beside it still names a directory that exists on one
 * machine. This closes that gap by replacing each spelling with the one the artifact field uses.
 *
 * <p>The replacement is a dictionary of exact substrings built from the request, and nothing else:
 * every {@code --application} and {@code --classpath} value the caller wrote, mapped to its measured
 * spelling. The longest text is tried first, so an entry that begins with another entry's text is
 * rewritten as itself rather than as its shorter neighbour, and a position inside an application
 * archive -- {@code app.jar!/BOOT-INF/lib/api.jar} -- is rewritten through the archive text it opens
 * with. Nothing is replaced twice: each match consumes its own text and the scan resumes after it.
 *
 * <p>A path the engine derived rather than the caller supplied, such as the companion a manifest
 * {@code Class-Path} resolves to beside its declaring JAR, is not in the dictionary and is left as
 * the engine wrote it. Guessing which fragments of a sentence are paths would corrupt prose, so the
 * request is the only vocabulary trusted here.
 */
final class EvidenceText {
    private static final Comparator<String> LONGEST_FIRST =
            Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder());

    private final Map<String, String> spellings;

    private EvidenceText(Map<String, String> spellings) {
        this.spellings = spellings;
    }

    /**
     * Builds the dictionary one request implies.
     *
     * @param root the root artifact paths are measured from
     * @param request the request the run answered
     * @return the spellings to apply, longest text first
     */
    static EvidenceText of(PathRoot root, VerificationRequest request) {
        Map<String, String> spellings = new LinkedHashMap<>();
        for (String display : longestFirst(request)) {
            String measured = root.artifact(display);
            if (!measured.equals(display)) {
                spellings.put(display, measured);
            }
        }
        return new EvidenceText(Collections.unmodifiableMap(spellings));
    }

    /**
     * Rewrites every detail of one finding's evidence.
     *
     * @param evidence the evidence as the engine composed it
     * @return the same lines, in the same order, spelling paths the way the artifact field does
     */
    List<Evidence> measured(List<Evidence> evidence) {
        return evidence.stream().map(detail -> new Evidence(rewritten(detail.detail()))).toList();
    }

    private String rewritten(String detail) {
        if (spellings.isEmpty()) {
            return detail;
        }
        StringBuilder rewritten = new StringBuilder();
        int index = 0;
        while (index < detail.length()) {
            index += appended(rewritten, detail, index);
        }
        return rewritten.toString();
    }

    /** Appends whichever spelling starts here, or the one character that does not start one. */
    private int appended(StringBuilder out, String detail, int index) {
        for (Map.Entry<String, String> spelling : spellings.entrySet()) {
            if (detail.startsWith(spelling.getKey(), index)) {
                out.append(spelling.getValue());
                return spelling.getKey().length();
            }
        }
        out.append(detail.charAt(index));
        return 1;
    }

    private static List<String> longestFirst(VerificationRequest request) {
        return Stream.concat(request.applications().stream(), request.classpath().stream())
                .map(Path::toString)
                .filter(display -> !display.isEmpty())
                .distinct()
                .sorted(LONGEST_FIRST)
                .toList();
    }
}
