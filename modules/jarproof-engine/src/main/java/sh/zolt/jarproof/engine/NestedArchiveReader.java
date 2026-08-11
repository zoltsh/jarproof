package sh.zolt.jarproof.engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Reads one library nested inside an application archive, from the outer archive's bytes.
 *
 * <p>Nothing is extracted. The library entry is read out of the open outer archive into memory against
 * the resource budget, and then enumerated as a stream of entries: a nested library is stored rather
 * than compressed precisely so a launcher can address it in place, and a stream is the honest way to
 * walk bytes that never became a file.
 *
 * <p>Streaming costs two things, and both are paid deliberately. An entry read from a stream may not
 * declare its size until it has been read, so every ceiling is applied twice — once to whatever the
 * entry claims, once to what it actually produced. And the entry order a stream presents is the
 * archive's, not the sorted order an artifact reports in, so the selected classes are collected by
 * base entry name before they are recorded.
 *
 * <p>Nesting stops here. An archive inside this library is a finding rather than another level of
 * reading: no launcher unpacks it, so its classes are on nobody's classpath, and descending anyway
 * would report a classpath that does not exist. Multi-release selection and publication identity, by
 * contrast, are this library's own business and are answered by this library's own manifest and its own
 * published records — which is why the walk captures those records as it passes them. A library shipped
 * inside an application is a published artifact like any other, and a coordinate nobody read is a
 * version conflict nobody reports.
 */
final class NestedArchiveReader {
    private final ClasspathEntry entry;
    private final NestedPosition position;
    private final ResourceBudget budget;
    private final int targetRelease;
    private final ArtifactScan scan;
    private final List<String> entryNames = new ArrayList<>();
    private final List<byte[]> records = new ArrayList<>();
    private Optional<Manifest> manifest = Optional.empty();

    private NestedArchiveReader(ClasspathEntry entry, ResourceBudget budget, int targetRelease) {
        this.entry = entry;
        this.position = entry.nested().orElseThrow();
        this.budget = budget;
        this.targetRelease = targetRelease;
        this.scan = new ArtifactScan(entry);
    }

    /**
     * Reads one nested library.
     *
     * @param entry the classpath position naming it inside its application archive
     * @param budget the run's resource budget
     * @param targetRelease the Java release the classpath will run on
     * @return the indexed artifact
     */
    static IndexedArtifact read(ClasspathEntry entry, ResourceBudget budget, int targetRelease) {
        return new NestedArchiveReader(entry, budget, targetRelease).open();
    }

    private IndexedArtifact open() {
        try (ZipFile outer = new ZipFile(entry.path().toFile())) {
            return index(library(outer));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private byte[] library(ZipFile outer) throws IOException {
        ZipEntry nested = outer.getEntry(position.path());
        if (nested == null) {
            throw new IllegalStateException(
                    "This application archive no longer holds the nested library " + position.path());
        }
        budget.checkNestedEntryBytes(nested.getSize(), entry.display());
        budget.checkCompressionRatio(nested.getCompressedSize(), nested.getSize(), entry.display());
        try (InputStream bytes = outer.getInputStream(nested)) {
            byte[] content = bytes.readNBytes(ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES + 1);
            budget.checkNestedEntryBytes(content.length, entry.display());
            budget.addExpandedBytes(content.length, entry.display());
            return content;
        }
    }

    private IndexedArtifact index(byte[] library) throws IOException {
        walk(library);
        List<String> sorted = entryNames.stream().sorted().toList();
        MultiReleaseSelection selection = ArchiveManifest.isMultiRelease(manifest)
                ? MultiReleaseSelection.versioned(sorted, targetRelease, entry.display())
                : MultiReleaseSelection.base(sorted, entry.display());
        selection.layoutFindings().forEach(scan::addFinding);
        selected(library, selection.classEntries()).forEach(
                (baseEntryName, classFile) -> scan.addClassEntry(
                        baseEntryName, selection.classEntries().get(baseEntryName), classFile));
        List<IndexedClass> classes = scan.classes();
        return new IndexedArtifact(
                entry,
                classes,
                ArtifactCoordinateReader.of(records, manifest),
                ArchiveManifest.sealedPackages(manifest, classes),
                scan.findings());
    }

    private void walk(byte[] library) throws IOException {
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(library))) {
            for (ZipEntry next = stream.getNextEntry(); next != null; next = stream.getNextEntry()) {
                if (!next.isDirectory()) {
                    describe(stream, next);
                }
            }
        }
    }

    /**
     * Records what one entry of the stream is, and charges it against the entry ceiling as it arrives.
     *
     * <p>The count is charged here rather than once the walk is over, because a ceiling consulted after
     * the work it bounds has already been paid for is not bounding anything: a library declaring more
     * entries than a run may read is refused at the entry that crosses the line, with none of the entries
     * behind it inflated. What is charged is how many entries have been seen, which is what the finished
     * walk would have reported, so a library refused before is refused now — same ceiling, same words.
     */
    private void describe(ZipInputStream stream, ZipEntry next) throws IOException {
        entryNames.add(next.getName());
        budget.countArchiveEntries(entryNames.size(), entry.display());
        if (ArchiveLayout.isNestedArchive(next.getName())) {
            scan.addFinding(NestedArchiveFinding.deeplyNested(position, next.getName()));
        }
        if (JarFile.MANIFEST_NAME.equals(next.getName())) {
            manifest = Optional.of(new Manifest(new ByteArrayInputStream(read(stream, next))));
        }
        if (ArtifactCoordinateReader.isPublishedRecord(next.getName())) {
            records.add(record(stream, next));
        }
    }

    /** Collects the bytes of the selected entries, keyed and ordered by the base name naming each class. */
    private Map<String, byte[]> selected(byte[] library, Map<String, String> classEntries) throws IOException {
        Map<String, String> baseNames = new LinkedHashMap<>();
        classEntries.forEach((baseEntryName, entryName) -> baseNames.put(entryName, baseEntryName));
        Map<String, byte[]> bytes = new TreeMap<>();
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(library))) {
            for (ZipEntry next = stream.getNextEntry(); next != null; next = stream.getNextEntry()) {
                String baseEntryName = baseNames.get(next.getName());
                if (baseEntryName != null) {
                    bytes.putIfAbsent(baseEntryName, read(stream, next));
                }
            }
        }
        return bytes;
    }

    /**
     * Reads one published record, which is no class file and answers to the ceiling on an entry read
     * whole instead — charged against the very budget the rest of this walk is charged against, because
     * a record read out of the outer archive costs the run exactly what every other read costs it.
     */
    private byte[] record(ZipInputStream stream, ZipEntry next) throws IOException {
        String entryName = next.getName();
        budget.checkNestedEntryBytes(next.getSize(), entryName);
        budget.checkCompressionRatio(next.getCompressedSize(), next.getSize(), entryName);
        byte[] content = stream.readNBytes(ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES + 1);
        budget.checkNestedEntryBytes(content.length, entryName);
        budget.addExpandedBytes(content.length, entryName);
        return content;
    }

    private byte[] read(ZipInputStream stream, ZipEntry next) throws IOException {
        String entryName = next.getName();
        budget.checkClassFileBytes(next.getSize(), entryName);
        budget.checkCompressionRatio(next.getCompressedSize(), next.getSize(), entryName);
        byte[] content = stream.readNBytes(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1);
        budget.checkClassFileBytes(content.length, entryName);
        budget.addExpandedBytes(content.length, entryName);
        return content;
    }
}
