package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * Reads one artifact's layout facts, entry by entry, without ever extracting it.
 *
 * <p>An archive is opened once and closed on every path out; a class directory is walked in sorted
 * order. Either way nothing is selected for a target release and no class is parsed: a class entry
 * is charged against the resource budget exactly as the class reader charges one — the declared
 * size and the compression ratio are checked before a byte is expanded — and then only the few
 * bytes of the class file header are actually read, because the major version is all an inspection
 * needs from it.
 *
 * <p>Every way the read can fail -- a path that is no archive, a directory that cannot be walked, an
 * entry that cannot be opened -- becomes one {@link IllegalArgumentException} naming the artifact,
 * decided in one place, because a summary of an artifact nobody can read would be a report of zero
 * facts rather than a failure.
 *
 * <p>The artifact is resolved to an absolute normalized read handle once, on the same terms
 * {@link ClasspathExpander} settles a classpath entry on, so the archive an inspection opens is the
 * archive it tested; the caller's own text is kept beside it and is the only text reported.
 */
final class Inspection {
    private static final String UNREADABLE = "Cannot inspect this artifact as an archive or a class directory: ";
    private static final char FILE_SYSTEM_SEPARATOR = '\\';
    private static final char ENTRY_SEPARATOR = '/';

    private final Path artifact;
    private final String display;
    private final ResourceBudget budget;
    private final InspectionTally tally;

    private Inspection(Path artifact) {
        this.artifact = artifact.toAbsolutePath().normalize();
        this.display = artifact.toString();
        this.budget = new ResourceBudget();
        this.tally = new InspectionTally(display);
    }

    /**
     * Reads one artifact.
     *
     * @param artifact path to a JAR or a class directory, kept exactly as the caller supplied it
     * @return the layout facts it presents
     */
    static ArtifactSummary of(Path artifact) {
        Inspection inspection = new Inspection(artifact);
        try {
            return Files.isDirectory(inspection.artifact) ? inspection.directory() : inspection.archive();
        } catch (IOException failure) {
            throw new IllegalArgumentException(UNREADABLE + artifact, failure);
        }
    }

    private ArtifactSummary archive() throws IOException {
        try (ZipFile opened = new ZipFile(artifact.toFile())) {
            budget.countArchiveEntries(opened.size(), display);
            for (String entryName : contentEntryNames(opened)) {
                tally.addEntry(entryName);
                if (InspectionLayout.isClassEntry(entryName)) {
                    tally.addClassFileHeader(header(opened, entryName));
                }
            }
            return tally.summary();
        }
    }

    private ArtifactSummary directory() throws IOException {
        List<String> entryNames = entryNames();
        budget.countArchiveEntries(entryNames.size(), display);
        for (String entryName : entryNames) {
            tally.addEntry(entryName);
            if (InspectionLayout.isClassEntry(entryName)) {
                tally.addClassFileHeader(header(artifact.resolve(entryName), entryName));
            }
        }
        return tally.summary();
    }

    private static List<String> contentEntryNames(ZipFile opened) {
        return opened.stream()
                .filter(candidate -> !candidate.isDirectory())
                .map(ZipEntry::getName)
                .sorted()
                .toList();
    }

    private List<String> entryNames() throws IOException {
        try (Stream<Path> children = Files.walk(artifact)) {
            return children.filter(Files::isRegularFile)
                    .map(child -> artifact.relativize(child).toString()
                            .replace(FILE_SYSTEM_SEPARATOR, ENTRY_SEPARATOR))
                    .sorted()
                    .toList();
        }
    }

    private byte[] header(ZipFile opened, String entryName) throws IOException {
        ZipEntry selected = opened.getEntry(entryName);
        budget.checkClassFileBytes(selected.getSize(), entryName);
        budget.checkCompressionRatio(selected.getCompressedSize(), selected.getSize(), entryName);
        try (InputStream bytes = opened.getInputStream(selected)) {
            return read(bytes, entryName);
        }
    }

    private byte[] header(Path file, String entryName) throws IOException {
        try (InputStream bytes = Files.newInputStream(file)) {
            budget.checkClassFileBytes(Files.size(file), entryName);
            return read(bytes, entryName);
        }
    }

    private byte[] read(InputStream bytes, String entryName) throws IOException {
        byte[] header = bytes.readNBytes(InspectionLayout.HEADER_BYTES);
        budget.addExpandedBytes(header.length, entryName);
        return header;
    }
}
