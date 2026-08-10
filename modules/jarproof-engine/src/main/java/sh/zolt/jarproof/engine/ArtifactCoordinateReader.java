package sh.zolt.jarproof.engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

/**
 * Recovers the identity an archive claims for itself, best effort and in confidence order.
 *
 * <p>Published Maven metadata is the strongest claim, so it is read first — but only when the archive
 * carries exactly one such record. An archive with several has been shaded together out of other
 * artifacts and honestly has no single identity, so guessing one would invent a version conflict.
 * The manifest title and version are the weaker fallback.
 *
 * <p>The rules themselves are stated over entry names and entry bytes rather than over archives,
 * because a library nested inside an application archive never becomes a file of its own: it is walked
 * as a stream, and its records are captured while that walk passes them. Both kinds of position
 * therefore answer to one copy of these rules instead of to two that could drift apart.
 */
final class ArtifactCoordinateReader {
    private static final String GROUP_PROPERTY = "groupId";
    private static final String ARTIFACT_PROPERTY = "artifactId";
    private static final String VERSION_PROPERTY = "version";
    private static final String UNKNOWN_GROUP = "";

    private ArtifactCoordinateReader() {
    }

    /**
     * Returns whether one entry is a record Maven publishes a coordinate in.
     *
     * @param entryName entry name inside an archive, in that archive's own terms
     * @return whether the entry carries a published coordinate
     */
    static boolean isPublishedRecord(String entryName) {
        return entryName.startsWith(ArchiveLayout.MAVEN_PREFIX)
                && entryName.endsWith(ArchiveLayout.POM_PROPERTIES_NAME);
    }

    /**
     * Decides the identity from the evidence one position yielded, however that position was read.
     *
     * @param records bytes of every published record the position carries
     * @param manifest the position's own main manifest
     * @return the claimed coordinate, or empty when the position claims none
     * @throws IOException when a record cannot be read
     */
    static Optional<ArtifactCoordinate> of(List<byte[]> records, Optional<Manifest> manifest) throws IOException {
        if (records.size() == 1) {
            Optional<ArtifactCoordinate> declared = fromProperties(records.get(0));
            if (declared.isPresent()) {
                return declared;
            }
        }
        return fromManifest(manifest);
    }

    /**
     * Reads the identity of one archive that is a file in its own right.
     *
     * @param archive the open archive
     * @param entryNames every entry name in the archive
     * @param manifest the archive's main manifest
     * @return the claimed coordinate, or empty when the archive claims none
     * @throws IOException when the archive cannot be read
     */
    static Optional<ArtifactCoordinate> read(
            ZipFile archive, List<String> entryNames, Optional<Manifest> manifest) throws IOException {
        List<byte[]> records = new ArrayList<>();
        for (String entryName : entryNames) {
            if (isPublishedRecord(entryName)) {
                records.add(record(archive, entryName));
            }
        }
        return of(records, manifest);
    }

    /** Reads one record whole, bounded by the ceiling every whole-entry read answers to. */
    private static byte[] record(ZipFile archive, String entryName) throws IOException {
        try (InputStream bytes = archive.getInputStream(archive.getEntry(entryName))) {
            return bytes.readNBytes(ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES);
        }
    }

    /** A record that does not decode, or that leaves any part of the coordinate out, claims nothing. */
    private static Optional<ArtifactCoordinate> fromProperties(byte[] record) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(record));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        String group = properties.getProperty(GROUP_PROPERTY);
        String artifact = properties.getProperty(ARTIFACT_PROPERTY);
        String version = properties.getProperty(VERSION_PROPERTY);
        if (group == null || artifact == null || version == null) {
            return Optional.empty();
        }
        return Optional.of(new ArtifactCoordinate(group, artifact, version));
    }

    private static Optional<ArtifactCoordinate> fromManifest(Optional<Manifest> manifest) {
        Optional<String> title = ArchiveManifest.mainAttribute(manifest, Attributes.Name.IMPLEMENTATION_TITLE);
        Optional<String> version = ArchiveManifest.mainAttribute(manifest, Attributes.Name.IMPLEMENTATION_VERSION);
        if (title.isEmpty() || version.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ArtifactCoordinate(UNKNOWN_GROUP, title.get(), version.get()));
    }
}
