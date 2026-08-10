package sh.zolt.jarproof.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Identifies where a finding lives: one classpath artifact and, when the finding concerns a
 * single class, the archive-internal class-file entry inside it.
 *
 * <p>The artifact path is kept exactly as the caller supplied it. Jarproof never absolutizes or
 * otherwise rewrites it, because identical inputs must produce identical reports on every machine.
 */
public record ArtifactLocation(String artifact, Optional<String> classEntry) {
    public ArtifactLocation {
        Objects.requireNonNull(artifact, "An artifact location needs an artifact path");
        if (artifact.isBlank()) {
            throw new IllegalArgumentException("An artifact path must repeat caller-supplied text");
        }
        Objects.requireNonNull(classEntry, "An artifact location needs a class-entry decision");
        if (classEntry.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("A class entry must name an entry inside the archive");
        }
    }

    /**
     * Locates a finding in a whole artifact.
     *
     * @param artifact artifact path exactly as the caller supplied it
     * @return a location without a class-file entry
     */
    public static ArtifactLocation ofArtifact(String artifact) {
        return new ArtifactLocation(artifact, Optional.empty());
    }

    /**
     * Locates a finding in one class-file entry of an artifact.
     *
     * @param artifact artifact path exactly as the caller supplied it
     * @param classEntry archive-internal class-file entry name
     * @return a location narrowed to that entry
     */
    public static ArtifactLocation ofClassEntry(String artifact, String classEntry) {
        return new ArtifactLocation(artifact, Optional.of(classEntry));
    }
}
