package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * The questions an inspection asks of one artifact entry: what it is, and what its opening bytes
 * declare.
 *
 * <p>These answers are deliberately raw. An inspection reports the artifact as it sits on disk, so
 * a version directory is a fact about the layout rather than a candidate for selection, and a
 * service configuration file is a registration rather than something to resolve. Nothing here
 * decides whether any of it is a problem.
 */
final class InspectionLayout {
    /** Bytes an inspection needs from the front of a class file: magic, minor, then major. */
    static final int HEADER_BYTES = 8;

    private static final int MAGIC_HIGH = 0xCAFE;
    private static final int MAGIC_LOW = 0xBABE;
    private static final int MAGIC_LOW_OFFSET = 2;
    private static final int MAJOR_VERSION_OFFSET = 6;
    private static final int BYTE_MASK = 0xFF;
    private static final int BYTE_BITS = 8;
    private static final char SEGMENT_END = '/';

    private InspectionLayout() {
    }

    /**
     * Returns whether an entry holds a compiled class.
     *
     * @param entryName entry name inside the artifact
     * @return whether the name ends in the class file suffix
     */
    static boolean isClassEntry(String entryName) {
        return entryName.endsWith(ArchiveLayout.CLASS_SUFFIX);
    }

    /**
     * Returns whether an entry is an archive the artifact carries inside itself.
     *
     * <p>Counted wherever it sits and never opened, which is what keeps the count a layout fact: an
     * application archive that carries its own dependencies is exactly the shape this answers for, and
     * an inspection describes that shape rather than reading through it.
     *
     * @param entryName entry name inside the artifact
     * @return whether the name ends in an archive suffix
     */
    static boolean isNestedArchive(String entryName) {
        return ArchiveLayout.isNestedArchive(entryName);
    }

    /**
     * Returns the service an entry registers providers for.
     *
     * @param entryName entry name inside the artifact
     * @return the service binary name, or empty when the entry is not a configuration file the
     *     runtime would consult
     */
    static Optional<String> serviceName(String entryName) {
        if (!entryName.startsWith(ServiceDeclaration.RESOURCE_PREFIX)) {
            return Optional.empty();
        }
        String name = entryName.substring(ServiceDeclaration.RESOURCE_PREFIX.length());
        return name.isEmpty() || name.indexOf(SEGMENT_END) >= 0 ? Optional.empty() : Optional.of(name);
    }

    /**
     * Returns the release directory an entry sits under.
     *
     * @param entryName entry name inside the artifact
     * @return the Java release the directory names, or empty when the entry is not under a numeric
     *     version directory
     */
    static Optional<Integer> multiReleaseVersion(String entryName) {
        if (!entryName.startsWith(ArchiveLayout.VERSIONS_PREFIX)) {
            return Optional.empty();
        }
        String remainder = entryName.substring(ArchiveLayout.VERSIONS_PREFIX.length());
        int end = remainder.indexOf(SEGMENT_END);
        return release(end < 0 ? remainder : remainder.substring(0, end));
    }

    /**
     * Returns the class file version the opening bytes of an entry declare.
     *
     * @param header the first {@link #HEADER_BYTES} bytes of an entry, or fewer when it holds fewer
     * @return the major version, or empty when these bytes are not the head of a class file
     */
    static Optional<Integer> majorVersion(byte[] header) {
        if (header.length < HEADER_BYTES || !isClassFile(header)) {
            return Optional.empty();
        }
        return Optional.of(unsigned(header, MAJOR_VERSION_OFFSET));
    }

    private static boolean isClassFile(byte[] header) {
        return unsigned(header, 0) == MAGIC_HIGH && unsigned(header, MAGIC_LOW_OFFSET) == MAGIC_LOW;
    }

    private static int unsigned(byte[] header, int offset) {
        return (header[offset] & BYTE_MASK) << BYTE_BITS | header[offset + 1] & BYTE_MASK;
    }

    private static Optional<Integer> release(String segment) {
        if (segment.isEmpty() || !segment.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(segment));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }
}
