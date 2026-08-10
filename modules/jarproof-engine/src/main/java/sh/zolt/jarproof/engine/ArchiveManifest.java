package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads the manifest facts that change how a classpath behaves, without interpreting anything else. */
final class ArchiveManifest {
    private static final Pattern SEPARATOR = Pattern.compile("\\s+");
    private static final char PACKAGE_SECTION_END = '/';

    private ArchiveManifest() {
    }

    /** Reads the main manifest of an already open archive, or empty when the archive declares none. */
    static Optional<Manifest> of(ZipFile archive) throws IOException {
        ZipEntry entry = archive.getEntry(JarFile.MANIFEST_NAME);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream bytes = archive.getInputStream(entry)) {
            return Optional.of(new Manifest(bytes));
        }
    }

    /**
     * Opens an archive purely to read its manifest.
     *
     * @param archive path to a JAR, kept exactly as the caller supplied it
     * @return the main manifest, or empty when the archive declares none
     * @throws IllegalArgumentException when the path is not a readable archive with a readable manifest
     */
    static Optional<Manifest> read(Path archive) {
        try (ZipFile opened = new ZipFile(archive.toFile())) {
            return of(opened);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read this JAR archive or its manifest: " + archive);
        }
    }

    /** Returns whether the main manifest opts this archive into multi-release entry selection. */
    static boolean isMultiRelease(Optional<Manifest> manifest) {
        return mainAttribute(manifest, Attributes.Name.MULTI_RELEASE).filter(Boolean::parseBoolean).isPresent();
    }

    /** Returns the manifest {@code Class-Path} entries exactly as written, in declaration order. */
    static List<String> classPath(Optional<Manifest> manifest) {
        Optional<String> declared = mainAttribute(manifest, Attributes.Name.CLASS_PATH);
        if (declared.isEmpty()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String candidate : SEPARATOR.split(declared.get())) {
            if (!candidate.isEmpty()) {
                entries.add(candidate);
            }
        }
        return List.copyOf(entries);
    }

    /**
     * Returns the packages this archive seals, sorted for stable reporting.
     *
     * <p>A sealed main attribute seals every package the archive declares classes in. A per-entry
     * section whose name ends in a path separator seals just that package.
     *
     * @param manifest main manifest of the archive
     * @param classes classes the archive declares, used to expand an archive-wide seal
     * @return sealed package internal prefixes
     */
    static List<String> sealedPackages(Optional<Manifest> manifest, List<IndexedClass> classes) {
        if (manifest.isEmpty()) {
            return List.of();
        }
        Manifest content = manifest.get();
        Set<String> sealed = new TreeSet<>();
        if (Boolean.parseBoolean(content.getMainAttributes().getValue(Attributes.Name.SEALED))) {
            classes.forEach(declared -> ArchiveLayout.packageOf(declared.internalName()).ifPresent(sealed::add));
        }
        content.getEntries().forEach((section, attributes) -> addSealedSection(sealed, section, attributes));
        return List.copyOf(sealed);
    }

    private static void addSealedSection(Set<String> sealed, String section, Attributes attributes) {
        boolean packageSection = !section.isEmpty() && section.charAt(section.length() - 1) == PACKAGE_SECTION_END;
        if (packageSection && Boolean.parseBoolean(attributes.getValue(Attributes.Name.SEALED))) {
            sealed.add(section.substring(0, section.length() - 1));
        }
    }

    /** Returns one main-attribute value, or empty when the archive or the attribute is absent. */
    static Optional<String> mainAttribute(Optional<Manifest> manifest, Attributes.Name name) {
        return manifest.map(content -> content.getMainAttributes().getValue(name));
    }
}
