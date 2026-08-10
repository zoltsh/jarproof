package sh.zolt.jarproof.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable machine-readable identity for a Jarproof finding. */
public final class FindingCode implements Comparable<FindingCode> {
    private static final Pattern FORMAT = Pattern.compile("JP[1-9][0-9]{3}");

    private final String value;

    private FindingCode(String value) {
        this.value = value;
    }

    /**
     * Creates a finding code from its canonical representation.
     *
     * @param value code such as {@code JP1003}
     * @return the validated code
     */
    public static FindingCode of(String value) {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Finding code must match JP followed by four digits: " + value);
        }
        return new FindingCode(value);
    }

    /** Returns the canonical code. */
    public String value() {
        return value;
    }

    @Override
    public int compareTo(FindingCode other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FindingCode code && value.equals(code.value);
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
