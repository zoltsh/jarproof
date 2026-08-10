package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Collects every service configuration file the effective classpath presents, in classpath order.
 *
 * <p>Only files sitting directly inside {@code META-INF/services/} are read, which is the one place
 * the runtime looks. A nested path below that directory is not a configuration file, and a versioned
 * copy under {@code META-INF/versions/} is deliberately left alone: a multi-release archive that
 * varies its service registrations by release is rare enough that reading one release's copy as if it
 * were the only one would trade a rare true finding for a common wrong one.
 *
 * <p>Archives are read entry by entry and closed on every path out, exactly as the class reader does,
 * and every byte comes out of the same resource budget: the per-entry ceiling and the compression
 * ratio are checked before anything is expanded, and what is expanded is counted against the run.
 * A class directory is listed rather than walked, because nothing below the services directory is
 * consulted.
 */
final class ServiceDeclarationReader {
    private final ResourceBudget budget;
    private final List<ServiceDeclaration> declarations = new ArrayList<>();

    private ServiceDeclarationReader(ResourceBudget budget) {
        this.budget = budget;
    }

    /**
     * Reads every service configuration file on one classpath.
     *
     * @param catalog the read classpath
     * @param budget the resource budget the read is charged against
     * @return one declaration per configuration file, in classpath order then entry-name order
     */
    static List<ServiceDeclaration> read(ArtifactCatalog catalog, ResourceBudget budget) {
        ServiceDeclarationReader reader = new ServiceDeclarationReader(budget);
        catalog.classpath().entries().forEach(reader::addEntry);
        return List.copyOf(reader.declarations);
    }

    private void addEntry(ClasspathEntry entry) {
        if (entry.kind() == EntryKind.DIRECTORY) {
            addDirectory(entry);
            return;
        }
        addArchive(entry);
    }

    private void addArchive(ClasspathEntry entry) {
        try (ZipFile archive = new ZipFile(entry.path().toFile())) {
            budget.countArchiveEntries(archive.size(), entry.display());
            for (String entryName : serviceEntryNames(archive)) {
                add(entry.display(), entryName.substring(ServiceDeclaration.RESOURCE_PREFIX.length()),
                        content(archive, entryName));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void addDirectory(ClasspathEntry entry) {
        Path services = entry.path().resolve(ServiceDeclaration.RESOURCE_PREFIX);
        if (!Files.isDirectory(services)) {
            return;
        }
        List<Path> files = serviceFilesIn(services);
        budget.countArchiveEntries(files.size(), entry.display());
        for (Path file : files) {
            String serviceName = file.getFileName().toString();
            add(entry.display(), serviceName,
                    content(file, ServiceDeclaration.RESOURCE_PREFIX + serviceName));
        }
    }

    private void add(String artifact, String serviceName, byte[] content) {
        declarations.add(new ServiceDeclaration(artifact, serviceName, ServiceFileParser.parse(content)));
    }

    private static List<String> serviceEntryNames(ZipFile archive) {
        return archive.stream()
                .filter(candidate -> !candidate.isDirectory())
                .map(ZipEntry::getName)
                .filter(ServiceDeclarationReader::isServiceEntry)
                .sorted()
                .toList();
    }

    /**
     * Accepts an entry sitting directly inside the services directory. The directory itself is
     * already excluded by the caller, because the prefix ends in a separator and an entry name that
     * ends in a separator is a directory entry by definition.
     */
    private static boolean isServiceEntry(String entryName) {
        return entryName.startsWith(ServiceDeclaration.RESOURCE_PREFIX)
                && entryName.indexOf('/', ServiceDeclaration.RESOURCE_PREFIX.length()) < 0;
    }

    private static List<Path> serviceFilesIn(Path services) {
        try (Stream<Path> children = Files.list(services)) {
            return children.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private byte[] content(ZipFile archive, String entryName) throws IOException {
        ZipEntry selected = archive.getEntry(entryName);
        budget.checkClassFileBytes(selected.getSize(), entryName);
        budget.checkCompressionRatio(selected.getCompressedSize(), selected.getSize(), entryName);
        try (InputStream bytes = archive.getInputStream(selected)) {
            byte[] content = bytes.readNBytes(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1);
            budget.checkClassFileBytes(content.length, entryName);
            budget.addExpandedBytes(content.length, entryName);
            return content;
        }
    }

    private byte[] content(Path file, String entryName) {
        try {
            budget.checkClassFileBytes(Files.size(file), entryName);
            byte[] content = Files.readAllBytes(file);
            budget.addExpandedBytes(content.length, entryName);
            return content;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
