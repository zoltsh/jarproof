package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads one archive entry by entry, without ever extracting it.
 *
 * <p>The archive is opened once, closed on every path out, and each selected class entry is read
 * against the resource budget: the declared size and compression ratio are checked before a single
 * byte is expanded, and the expansion itself is capped, so a crafted archive cannot trade a few
 * kilobytes on disk for gigabytes in memory. The manifest is not parsed again here: assembling the
 * classpath had to read it to follow a {@code Class-Path}, so the entry carries what it found and this
 * read spends its open handle on class bytes alone.
 *
 * <p>Three kinds of classpath position are read here, and they differ only in which entries of the
 * archive they present. An ordinary archive presents all of them. The classes root of an application
 * archive that carries its own dependencies presents the entries under that root, named relative to
 * it, and consults no manifest at all — the launcher treats that root as a directory, so, exactly as
 * for a class directory, the root announces no multi-release layout, claims no publication identity,
 * and seals no package. The same application archive read for its own top level presents everything
 * outside the areas its launcher addresses separately, because those areas are already on the
 * classpath under their own positions and their entry names are not class names.
 */
final class ArchiveArtifactReader {
    private final ClasspathEntry entry;
    private final ResourceBudget budget;
    private final int targetRelease;

    private ArchiveArtifactReader(ClasspathEntry entry, ResourceBudget budget, int targetRelease) {
        this.entry = entry;
        this.budget = budget;
        this.targetRelease = targetRelease;
    }

    /**
     * Reads one archive.
     *
     * @param entry the classpath position holding the archive
     * @param budget the run's resource budget
     * @param targetRelease the Java release the classpath will run on
     * @return the indexed artifact
     */
    static IndexedArtifact read(ClasspathEntry entry, ResourceBudget budget, int targetRelease) {
        return new ArchiveArtifactReader(entry, budget, targetRelease).open();
    }

    private IndexedArtifact open() {
        try (ZipFile archive = new ZipFile(entry.path().toFile())) {
            return index(archive);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private IndexedArtifact index(ZipFile archive) throws IOException {
        budget.countArchiveEntries(archive.size(), entry.display());
        List<String> entryNames = contentEntryNames(archive);
        Optional<Manifest> declared = entry.manifest();
        Optional<Manifest> manifest = isClassesRoot() ? Optional.empty() : declared;
        MultiReleaseSelection selection = select(presented(entryNames, declared), manifest);
        ArtifactScan scan = new ArtifactScan(entry);
        selection.layoutFindings().forEach(scan::addFinding);
        for (Map.Entry<String, String> selected : selection.classEntries().entrySet()) {
            scan.addClassEntry(selected.getKey(), selected.getValue(), read(archive, inside(selected.getValue())));
        }
        List<IndexedClass> classes = scan.classes();
        return new IndexedArtifact(
                entry,
                classes,
                coordinate(archive, entryNames, manifest),
                ArchiveManifest.sealedPackages(manifest, classes),
                scan.findings());
    }

    private static List<String> contentEntryNames(ZipFile archive) {
        return archive.stream()
                .filter(candidate -> !candidate.isDirectory())
                .map(ZipEntry::getName)
                .sorted()
                .toList();
    }

    /** Returns the entry names this position presents, in the terms it presents them. */
    private List<String> presented(List<String> entryNames, Optional<Manifest> declared) {
        if (isClassesRoot()) {
            String root = entry.nested().orElseThrow().path();
            return entryNames.stream()
                    .filter(name -> name.startsWith(root))
                    .map(name -> name.substring(root.length()))
                    .toList();
        }
        if (entry.kind() != EntryKind.HOST_ARCHIVE) {
            return entryNames;
        }
        Optional<BootLayout> layout = BootLayout.of(declared, entryNames);
        return entryNames.stream().filter(name -> layout.filter(carried -> carried.holds(name)).isEmpty()).toList();
    }

    /** Returns the entry the presented name's bytes live in, which the classes root addresses by prefix. */
    private String inside(String presentedName) {
        return isClassesRoot() ? entry.nested().orElseThrow().path() + presentedName : presentedName;
    }

    private Optional<ArtifactCoordinate> coordinate(
            ZipFile archive, List<String> entryNames, Optional<Manifest> manifest) throws IOException {
        return isClassesRoot()
                ? Optional.empty()
                : ArtifactCoordinateReader.read(archive, entryNames, manifest);
    }

    private boolean isClassesRoot() {
        return entry.kind() == EntryKind.NESTED_CLASSES;
    }

    private MultiReleaseSelection select(List<String> entryNames, Optional<Manifest> manifest) {
        return ArchiveManifest.isMultiRelease(manifest)
                ? MultiReleaseSelection.versioned(entryNames, targetRelease, entry.display())
                : MultiReleaseSelection.base(entryNames, entry.display());
    }

    private byte[] read(ZipFile archive, String entryName) throws IOException {
        ZipEntry selected = archive.getEntry(entryName);
        budget.checkClassFileBytes(selected.getSize(), entryName);
        budget.checkCompressionRatio(selected.getCompressedSize(), selected.getSize(), entryName);
        try (InputStream bytes = archive.getInputStream(selected)) {
            byte[] classFile = bytes.readNBytes(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1);
            budget.checkClassFileBytes(classFile.length, entryName);
            budget.addExpandedBytes(classFile.length, entryName);
            return classFile;
        }
    }
}
