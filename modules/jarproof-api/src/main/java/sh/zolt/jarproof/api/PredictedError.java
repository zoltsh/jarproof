package sh.zolt.jarproof.api;

import java.util.List;
import java.util.Objects;

/**
 * The runtime failure a finding forecasts, named after the throwable the JVM raises.
 *
 * <p>This is a validated value type rather than an enum so new failure categories can join in a
 * minor release without breaking previously compiled exhaustive switches. The accepted names form
 * a closed set at any given release; {@link #of(String)} rejects everything outside it and always
 * returns the canonical instance.
 */
public final class PredictedError implements Comparable<PredictedError> {
    /** The finding forecasts no runtime failure. */
    public static final PredictedError NONE = new PredictedError("None");

    /** Resolving a referenced class fails at runtime. */
    public static final PredictedError NO_CLASS_DEF_FOUND_ERROR = new PredictedError("NoClassDefFoundError");

    /** Resolving a referenced method fails at runtime. */
    public static final PredictedError NO_SUCH_METHOD_ERROR = new PredictedError("NoSuchMethodError");

    /** Resolving a referenced field fails at runtime. */
    public static final PredictedError NO_SUCH_FIELD_ERROR = new PredictedError("NoSuchFieldError");

    /** A resolved type or member contradicts the shape of its reference. */
    public static final PredictedError INCOMPATIBLE_CLASS_CHANGE_ERROR =
            new PredictedError("IncompatibleClassChangeError");

    /** A resolved type or member is not accessible from the referencing class. */
    public static final PredictedError ILLEGAL_ACCESS_ERROR = new PredictedError("IllegalAccessError");

    /** An invoked method resolves to a declaration with no implementation. */
    public static final PredictedError ABSTRACT_METHOD_ERROR = new PredictedError("AbstractMethodError");

    /** A class file is newer than the target release supports. */
    public static final PredictedError UNSUPPORTED_CLASS_VERSION_ERROR =
            new PredictedError("UnsupportedClassVersionError");

    /** Loading a declared service provider fails inside {@code ServiceLoader}. */
    public static final PredictedError SERVICE_CONFIGURATION_ERROR =
            new PredictedError("ServiceConfigurationError");

    private static final List<PredictedError> KNOWN = List.of(
            NONE,
            NO_CLASS_DEF_FOUND_ERROR,
            NO_SUCH_METHOD_ERROR,
            NO_SUCH_FIELD_ERROR,
            INCOMPATIBLE_CLASS_CHANGE_ERROR,
            ILLEGAL_ACCESS_ERROR,
            ABSTRACT_METHOD_ERROR,
            UNSUPPORTED_CLASS_VERSION_ERROR,
            SERVICE_CONFIGURATION_ERROR);

    private final String value;

    private PredictedError(String value) {
        this.value = value;
    }

    /**
     * Returns the canonical instance for a predicted-failure name.
     *
     * @param value a throwable simple name such as {@code NoSuchMethodError}, or {@code None}
     * @return the canonical predicted error carrying that name
     */
    public static PredictedError of(String value) {
        Objects.requireNonNull(value, "A predicted failure needs its throwable name");
        for (PredictedError candidate : KNOWN) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown predicted runtime failure: " + value);
    }

    /** Returns the throwable simple name this prediction carries, or {@code None}. */
    public String value() {
        return value;
    }

    @Override
    public int compareTo(PredictedError other) {
        return value.compareTo(Objects.requireNonNull(other, "A comparison needs its other prediction").value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PredictedError prediction && value.equals(prediction.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
