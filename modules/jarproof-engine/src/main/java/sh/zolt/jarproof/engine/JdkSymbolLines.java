package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Encodes and decodes the individual lines of a bundled JDK symbol resource.
 *
 * <p>The complete line grammar, including the header, is documented on
 * {@link JdkSymbolCatalog}. This type owns the single implementation of it, so the
 * generator that writes a resource and the reader that loads one can never drift.
 */
final class JdkSymbolLines {
    private static final String MAGIC = "#jarproof-jdk-symbols";
    private static final String FIELD_SEPARATOR = "\t";
    private static final String LIST_SEPARATOR = ",";
    private static final int FORMAT_VERSION = 2;
    private static final int HEADER_FIELDS = 5;
    private static final int CLASS_FIELDS = 7;
    private static final int MEMBER_FIELDS = 3;
    private static final int DECIMAL = 10;
    private static final int HEXADECIMAL = 16;

    private JdkSymbolLines() {
    }

    /** Builds the first line of a resource, stamping the runtime the data was generated from. */
    static String header(int javaRelease, int classCount, String sourceRuntime) {
        return MAGIC + FIELD_SEPARATOR + FORMAT_VERSION + FIELD_SEPARATOR + javaRelease
                + FIELD_SEPARATOR + classCount + FIELD_SEPARATOR + sourceRuntime;
    }

    /** Returns the Java release a header line declares. */
    static int releaseOf(String headerLine) {
        return number(headerFields(headerLine)[2], DECIMAL);
    }

    /** Returns the class count a header line declares. */
    static int classCountOf(String headerLine) {
        return number(headerFields(headerLine)[3], DECIMAL);
    }

    /** Returns the runtime version of the JDK whose signature archive produced the resource. */
    static String sourceRuntimeOf(String headerLine) {
        String stamped = headerFields(headerLine)[4];
        if (stamped.isBlank()) {
            throw new IllegalStateException("A JDK symbol resource must name its source runtime");
        }
        return stamped;
    }

    /** Writes one class as a single line, without its terminating newline. */
    static String encode(JdkSymbolEntry entry) {
        ClassShape shape = entry.shape();
        StringBuilder line = new StringBuilder();
        line.append(shape.internalName()).append(FIELD_SEPARATOR)
                .append(entry.module()).append(FIELD_SEPARATOR)
                .append(Integer.toHexString(shape.accessFlags())).append(FIELD_SEPARATOR)
                .append(shape.superInternalName().orElse("")).append(FIELD_SEPARATOR)
                .append(String.join(LIST_SEPARATOR, shape.interfaceInternalNames())).append(FIELD_SEPARATOR)
                .append(shape.nestHostInternalName().orElse("")).append(FIELD_SEPARATOR)
                .append(String.join(LIST_SEPARATOR, shape.nestMemberInternalNames()));
        for (MemberShape member : shape.members()) {
            line.append(FIELD_SEPARATOR).append(member.name())
                    .append(FIELD_SEPARATOR).append(member.descriptor())
                    .append(FIELD_SEPARATOR).append(Integer.toHexString(member.accessFlags()));
        }
        return line.toString();
    }

    /** Reads one class line back into its owning module and declared shape. */
    static JdkSymbolEntry decode(String line) {
        String[] fields = line.split(FIELD_SEPARATOR, -1);
        if (fields.length < CLASS_FIELDS || (fields.length - CLASS_FIELDS) % MEMBER_FIELDS != 0) {
            throw new IllegalStateException("A JDK symbol class line needs " + CLASS_FIELDS
                    + " fields followed by whole member triples: " + line);
        }
        return new JdkSymbolEntry(fields[1], new ClassShape(
                fields[0],
                number(fields[2], HEXADECIMAL),
                present(fields[3]),
                listed(fields[4]),
                present(fields[5]),
                listed(fields[6]),
                membersOf(fields)));
    }

    private static String[] headerFields(String headerLine) {
        String[] fields = headerLine.split(FIELD_SEPARATOR, -1);
        if (fields.length != HEADER_FIELDS || !fields[0].equals(MAGIC)) {
            throw new IllegalStateException("Not a jarproof JDK symbol resource: " + headerLine);
        }
        int version = number(fields[1], DECIMAL);
        if (version != FORMAT_VERSION) {
            throw new IllegalStateException("JDK symbol resource format version " + version
                    + " is not the one this build reads, version " + FORMAT_VERSION);
        }
        return fields;
    }

    private static List<MemberShape> membersOf(String[] fields) {
        List<MemberShape> members = new ArrayList<>((fields.length - CLASS_FIELDS) / MEMBER_FIELDS);
        for (int index = CLASS_FIELDS; index < fields.length; index += MEMBER_FIELDS) {
            members.add(new MemberShape(
                    fields[index], fields[index + 1], number(fields[index + 2], HEXADECIMAL)));
        }
        return members;
    }

    private static List<String> listed(String field) {
        return field.isEmpty() ? List.of() : List.of(field.split(LIST_SEPARATOR, -1));
    }

    private static Optional<String> present(String field) {
        return field.isEmpty() ? Optional.empty() : Optional.of(field);
    }

    private static int number(String text, int radix) {
        try {
            return Integer.parseInt(text, radix);
        } catch (NumberFormatException malformed) {
            throw new IllegalStateException(
                    "A JDK symbol resource carries a number that will not parse: " + text, malformed);
        }
    }
}
