package sh.zolt.jarproof.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Identifies where a finding lives: one classpath artifact, the archive-internal class-file entry
 * inside it when the finding concerns a single class, and the source coordinates that class file
 * carried if it was compiled with debug information.
 *
 * <p>The artifact path is kept exactly as the caller supplied it. Jarproof never absolutizes or
 * otherwise rewrites it, because identical inputs must produce identical reports on every machine.
 *
 * <p>{@code sourceFile} repeats the class file's {@code SourceFile} attribute verbatim, which is a
 * bare file name such as {@code OrderValidator.java} and never a path, and {@code line} is the
 * 1-based line the reference sits on. Both are absent for a class compiled without debug
 * information, and {@code line} is absent as well for a reference no line table covers. A line
 * without a class entry is refused outright: a line belongs to a class.
 *
 * <p>Neither component takes part in a baseline fingerprint, which stays the code, the artifact,
 * the class entry, and the subject. Recompiling with different debug settings, or moving a call site
 * down a file, therefore cannot invalidate an accepted finding.
 */
public record ArtifactLocation(
        String artifact,
        Optional<String> classEntry,
        Optional<String> sourceFile,
        Optional<Integer> line) {
    private static final int FIRST_LINE = 1;

    public ArtifactLocation {
        Objects.requireNonNull(artifact, "An artifact location needs an artifact path");
        if (artifact.isBlank()) {
            throw new IllegalArgumentException("An artifact path must repeat caller-supplied text");
        }
        Objects.requireNonNull(classEntry, "An artifact location needs a class-entry decision");
        if (classEntry.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("A class entry must name an entry inside the archive");
        }
        Objects.requireNonNull(sourceFile, "An artifact location needs a source-file decision");
        if (sourceFile.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("A source file must name what the class was compiled from");
        }
        Objects.requireNonNull(line, "An artifact location needs a source-line decision");
        if (line.filter(number -> number < FIRST_LINE).isPresent()) {
            throw new IllegalArgumentException("A source line is counted from one");
        }
        if (line.isPresent() && classEntry.isEmpty()) {
            throw new IllegalArgumentException("A source line needs the class entry it is a line of");
        }
    }

    /**
     * Locates a finding in a whole artifact.
     *
     * @param artifact artifact path exactly as the caller supplied it
     * @return a location without a class-file entry
     */
    public static ArtifactLocation ofArtifact(String artifact) {
        return new ArtifactLocation(artifact, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Locates a finding in one class-file entry of an artifact.
     *
     * @param artifact artifact path exactly as the caller supplied it
     * @param classEntry archive-internal class-file entry name
     * @return a location narrowed to that entry, with no source coordinates
     */
    public static ArtifactLocation ofClassEntry(String artifact, String classEntry) {
        return new ArtifactLocation(artifact, Optional.of(classEntry), Optional.empty(), Optional.empty());
    }

    /**
     * Locates a finding in one class-file entry, carrying whatever source coordinates that class
     * file declared.
     *
     * @param artifact artifact path exactly as the caller supplied it
     * @param classEntry archive-internal class-file entry name
     * @param sourceFile the entry's {@code SourceFile} attribute, absent without debug information
     * @param line the 1-based source line, absent when no line table covers the reference
     * @return a location narrowed as far as the class file allows
     */
    public static ArtifactLocation ofSource(
            String artifact, String classEntry, Optional<String> sourceFile, Optional<Integer> line) {
        return new ArtifactLocation(artifact, Optional.of(classEntry), sourceFile, line);
    }
}
