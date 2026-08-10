package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
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
 */
final class ArtifactCoordinateReader {
    private static final String GROUP_PROPERTY = "groupId";
    private static final String ARTIFACT_PROPERTY = "artifactId";
    private static final String VERSION_PROPERTY = "version";
    private static final String UNKNOWN_GROUP = "";

    private ArtifactCoordinateReader() {
    }

    /**
     * Reads the identity of one archive.
     *
     * @param archive the open archive
     * @param entryNames every entry name in the archive
     * @param manifest the archive's main manifest
     * @return the claimed coordinate, or empty when the archive claims none
     * @throws IOException when the archive cannot be read
     */
    static Optional<ArtifactCoordinate> read(
            ZipFile archive, List<String> entryNames, Optional<Manifest> manifest) throws IOException {
        List<String> published = entryNames.stream()
                .filter(name -> name.startsWith(ArchiveLayout.MAVEN_PREFIX))
                .filter(name -> name.endsWith(ArchiveLayout.POM_PROPERTIES_NAME))
                .toList();
        if (published.size() == 1) {
            Optional<ArtifactCoordinate> declared = fromProperties(archive, published.get(0));
            if (declared.isPresent()) {
                return declared;
            }
        }
        return fromManifest(manifest);
    }

    private static Optional<ArtifactCoordinate> fromProperties(ZipFile archive, String entryName) throws IOException {
        Properties properties = new Properties();
        try (InputStream bytes = archive.getInputStream(archive.getEntry(entryName))) {
            properties.load(bytes);
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
