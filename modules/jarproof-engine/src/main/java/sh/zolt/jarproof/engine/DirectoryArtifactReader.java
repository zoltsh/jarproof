package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a bare directory of class files the way the launcher treats one.
 *
 * <p>A directory is addressed by relative path and nothing else: a manifest inside it is not
 * consulted, so it announces no multi-release layout, claims no publication identity, and seals no
 * package. Entries are visited in sorted order so the same directory always indexes identically.
 */
final class DirectoryArtifactReader {
    private static final char FILE_SYSTEM_SEPARATOR = '\\';
    private static final char ENTRY_SEPARATOR = '/';

    private DirectoryArtifactReader() {
    }

    /**
     * Reads one class directory.
     *
     * @param entry the classpath position holding the directory
     * @param budget the run's resource budget
     * @return the indexed artifact
     */
    static IndexedArtifact read(ClasspathEntry entry, ResourceBudget budget) {
        List<String> entryNames = names(entry.path());
        budget.countArchiveEntries(entryNames.size(), entry.display());
        MultiReleaseSelection selection = MultiReleaseSelection.base(entryNames, entry.display());
        ArtifactScan scan = new ArtifactScan(entry);
        selection.layoutFindings().forEach(scan::addFinding);
        for (Map.Entry<String, String> selected : selection.classEntries().entrySet()) {
            scan.addClassEntry(
                    selected.getKey(), selected.getValue(), read(entry.path(), selected.getValue(), budget));
        }
        return new IndexedArtifact(entry, scan.classes(), Optional.empty(), List.of(), scan.findings());
    }

    private static List<String> names(Path directory) {
        try (Stream<Path> children = Files.walk(directory)) {
            return children.filter(Files::isRegularFile)
                    .map(child -> directory.relativize(child).toString()
                            .replace(FILE_SYSTEM_SEPARATOR, ENTRY_SEPARATOR))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] read(Path directory, String entryName, ResourceBudget budget) {
        Path file = directory.resolve(entryName);
        try {
            budget.checkClassFileBytes(Files.size(file), entryName);
            byte[] classFile = Files.readAllBytes(file);
            budget.addExpandedBytes(classFile.length, entryName);
            return classFile;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
