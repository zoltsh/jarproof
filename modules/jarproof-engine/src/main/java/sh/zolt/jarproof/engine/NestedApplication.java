package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import sh.zolt.jarproof.api.Finding;

/**
 * Expands one application archive that carries its own dependencies into the positions its launcher
 * would search, without extracting anything.
 *
 * <p>The archive contributes three kinds of position, in this order: its classes root, as the
 * application origin, because that is the first-party bytecode under test; then each nested library,
 * as a classpath origin, because a library shipped inside an application is still a library; then the
 * archive itself, for the launcher classes sitting at its top level, which is all a runtime sees of it
 * directly. Nothing else about the ordinary reading of an archive changes, so the manifest
 * {@code Class-Path} of the outer archive still chains from the position the archive itself occupies.
 *
 * <p>Only an application root is expanded this way. The same archive named as a classpath entry stays
 * one ordinary entry, because a runtime handed it on the classpath sees exactly what it declares at
 * its top level and never looks inside its nested areas either.
 *
 * <p>Libraries follow the classpath index when the archive carries one, and the archive's own entry
 * order otherwise. A library the index omits still follows the indexed ones, because dropping a
 * library the launcher can reach would hide every class in it.
 */
final class NestedApplication {
    private final Path handle;
    private final String display;
    private final ResourceBudget budget;
    private final BootLayout layout;
    private final List<ClasspathEntry> entries = new ArrayList<>();
    private final List<Finding> findings = new ArrayList<>();

    private NestedApplication(Path handle, String display, ResourceBudget budget, BootLayout layout) {
        this.handle = handle;
        this.display = display;
        this.budget = budget;
        this.layout = layout;
    }

    /**
     * Reads one application archive to see whether it carries its own dependencies.
     *
     * <p>The entry ceiling answers before a single name is collected, because collecting them is the
     * work that ceiling exists to bound. The count is a number the directory this open has already read
     * declares, so refusing an archive that declares more entries than a run may read costs nothing,
     * while accumulating one name for each of them first would cost precisely what it was sent for.
     *
     * @param handle read handle of the archive, already resolved against the engine's base
     * @param display the caller's own text for that archive, which is all any report shows
     * @param budget the run's resource budget
     * @return the expansion, or empty when the archive is an ordinary one
     * @throws IllegalArgumentException when the path is not a readable archive
     * @throws IllegalStateException when the archive declares more entries than a run may read
     */
    static Optional<NestedApplication> of(Path handle, String display, ResourceBudget budget) {
        try (ZipFile archive = new ZipFile(handle.toFile())) {
            budget.countArchiveEntries(archive.size(), display);
            List<? extends ZipEntry> contents = archive.stream()
                    .filter(candidate -> !candidate.isDirectory())
                    .toList();
            Optional<Manifest> declared = ArchiveManifest.of(archive);
            Optional<BootLayout> layout = BootLayout.of(
                    declared, contents.stream().map(ZipEntry::getName).toList());
            if (layout.isEmpty()) {
                return Optional.empty();
            }
            NestedApplication application = new NestedApplication(handle, display, budget, layout.get());
            application.expand(archive, contents, declared);
            return Optional.of(application);
        } catch (IOException exception) {
            throw new IllegalArgumentException(ArchiveManifest.UNREADABLE + display, exception);
        }
    }

    /** Returns the classpath positions this archive contributes, in launcher order. */
    List<ClasspathEntry> entries() {
        return List.copyOf(entries);
    }

    /** Returns what reading the layout already proved. */
    List<Finding> findings() {
        return List.copyOf(findings);
    }

    private void expand(ZipFile archive, List<? extends ZipEntry> contents, Optional<Manifest> declared)
            throws IOException {
        entries.add(position(layout.classesRoot(), EntryKind.NESTED_CLASSES, ClasspathOrigin.APPLICATION));
        for (ZipEntry library : libraries(archive, contents)) {
            if (library.getMethod() != ZipEntry.STORED) {
                findings.add(NestedArchiveFinding.compressedLibrary(display, library.getName()));
            }
            entries.add(position(library.getName(), EntryKind.NESTED_ARCHIVE, ClasspathOrigin.CLASSPATH));
        }
        entries.add(new ClasspathEntry(
                display, handle, EntryKind.HOST_ARCHIVE, ClasspathOrigin.CLASSPATH,
                Optional.empty(), Optional.empty(), declared));
    }

    private ClasspathEntry position(String path, EntryKind kind, ClasspathOrigin origin) {
        return new ClasspathEntry(
                BootLayout.nestedDisplay(display, path),
                handle,
                kind,
                origin,
                Optional.empty(),
                Optional.of(new NestedPosition(display, path)));
    }

    /**
     * Orders the nested libraries: those the index names first, in index order, then the rest as the
     * archive declares them. An indexed path matching no nested library of this archive is reported
     * rather than searched for elsewhere, because the index is a statement about this directory.
     */
    private List<ZipEntry> libraries(ZipFile archive, List<? extends ZipEntry> contents) throws IOException {
        Map<String, ZipEntry> declared = new LinkedHashMap<>();
        contents.stream()
                .filter(candidate -> layout.isLibrary(candidate.getName()))
                .forEach(candidate -> declared.putIfAbsent(candidate.getName(), candidate));
        List<ZipEntry> ordered = new ArrayList<>();
        for (String indexed : indexedPaths(archive)) {
            ZipEntry named = declared.remove(indexed);
            if (named == null) {
                findings.add(NestedArchiveFinding.absentIndexEntry(display, indexed));
            } else {
                ordered.add(named);
            }
        }
        ordered.addAll(declared.values());
        return List.copyOf(ordered);
    }

    private List<String> indexedPaths(ZipFile archive) throws IOException {
        String indexPath = layout.indexPath();
        ZipEntry index = archive.getEntry(indexPath);
        if (index == null) {
            return List.of();
        }
        budget.checkNestedEntryBytes(index.getSize(), indexPath);
        budget.checkCompressionRatio(index.getCompressedSize(), index.getSize(), indexPath);
        try (InputStream bytes = archive.getInputStream(index)) {
            byte[] content = bytes.readNBytes(ResourceBudget.MAXIMUM_NESTED_ENTRY_BYTES + 1);
            budget.checkNestedEntryBytes(content.length, indexPath);
            budget.addExpandedBytes(content.length, indexPath);
            return BootLayout.indexedPaths(new String(content, StandardCharsets.UTF_8));
        }
    }
}
