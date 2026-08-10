package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;

/**
 * Reads and writes a baseline file, using the same canonical JSON rules as the report writers.
 *
 * <p>The envelope is versioned so the format can change without silently misreading old files: a
 * version this build does not implement is refused, never guessed at. Members are written in a
 * fixed order and fingerprints are already sorted, so re-recording an unchanged baseline produces
 * an unchanged file and a review diff shows only real movement.
 */
final class BaselineJson {
    /** Member holding the identity of the runtime symbol profile a baseline was recorded against. */
    static final String PROFILE = "profile";

    private static final String BASELINE_VERSION = "baselineVersion";
    private static final String CURRENT_VERSION = "1";
    private static final String FINGERPRINTS = "fingerprints";
    private static final String NOT_AN_OBJECT = "A baseline file must contain a JSON object";
    private static final String UNSUPPORTED_VERSION = "Unsupported baseline version: ";
    private static final String MISSING_MEMBER = "The baseline is missing ";
    private static final String WRONG_KIND = "The baseline holds the wrong kind of value for ";
    private static final String UNKNOWN_VALUE = "The baseline holds an unknown value for ";

    private BaselineJson() {
    }

    /**
     * Renders a baseline as a canonical JSON document.
     *
     * @param baseline the accepted findings and what they were accepted against
     * @return the complete document, terminated by a single LF
     */
    static String write(BaselineDocument baseline) {
        JsonText json = new JsonText();
        json.beginObject();
        json.name(BASELINE_VERSION).value(CURRENT_VERSION);
        RequestJson.appendFields(json, baseline.targetJava(), baseline.preview(), baseline.scope());
        json.name(PROFILE).value(baseline.profile());
        json.name(FINGERPRINTS).beginArray();
        for (String fingerprint : baseline.fingerprints()) {
            json.value(fingerprint);
        }
        json.endArray();
        json.endObject();
        return json.document();
    }

    /**
     * Parses a baseline file.
     *
     * @param text the file contents
     * @return the baseline it records
     * @throws IllegalArgumentException when the text is not a baseline this build understands
     */
    static BaselineDocument read(String text) {
        Object parsed = JsonScanner.parse(text);
        if (!(parsed instanceof Map<?, ?> envelope)) {
            throw new IllegalArgumentException(NOT_AN_OBJECT);
        }
        String version = stringOf(envelope, BASELINE_VERSION);
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalArgumentException(UNSUPPORTED_VERSION + version);
        }
        return new BaselineDocument(
                numberOf(envelope, RequestJson.TARGET_JAVA),
                enumOf(envelope, RequestJson.PREVIEW, PreviewMode.class),
                enumOf(envelope, RequestJson.SCOPE, Scope.class),
                stringOf(envelope, PROFILE),
                fingerprintsOf(envelope));
    }

    private static Object memberOf(Map<?, ?> envelope, String key) {
        Object member = envelope.get(key);
        if (member == null) {
            throw new IllegalArgumentException(MISSING_MEMBER + key);
        }
        return member;
    }

    private static String stringOf(Map<?, ?> envelope, String key) {
        Object member = memberOf(envelope, key);
        if (member instanceof String value) {
            return value;
        }
        throw new IllegalArgumentException(WRONG_KIND + key);
    }

    private static int numberOf(Map<?, ?> envelope, String key) {
        Object member = memberOf(envelope, key);
        if (member instanceof Integer value) {
            return value;
        }
        throw new IllegalArgumentException(WRONG_KIND + key);
    }

    private static <E extends Enum<E>> E enumOf(Map<?, ?> envelope, String key, Class<E> vocabulary) {
        String value = stringOf(envelope, key);
        return CanonicalName.parse(vocabulary, value)
                .orElseThrow(() -> new IllegalArgumentException(UNKNOWN_VALUE + key));
    }

    private static List<String> fingerprintsOf(Map<?, ?> envelope) {
        if (!(memberOf(envelope, FINGERPRINTS) instanceof List<?> elements)) {
            throw new IllegalArgumentException(WRONG_KIND + FINGERPRINTS);
        }
        List<String> fingerprints = new ArrayList<>();
        for (Object element : elements) {
            if (!(element instanceof String fingerprint)) {
                throw new IllegalArgumentException(WRONG_KIND + FINGERPRINTS);
            }
            fingerprints.add(fingerprint);
        }
        return fingerprints;
    }
}
