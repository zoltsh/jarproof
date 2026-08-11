package sh.zolt.jarproof.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds application archives that carry their own dependencies, the way a launcher-aware packager
 * does.
 *
 * <p>The one thing that needs care is storing a nested library rather than compressing it. A stored
 * entry carries no data descriptor, so {@link ZipOutputStream} refuses it unless the size and the CRC
 * are known before the first byte is written — this fixture computes both, which is exactly the
 * discipline a real packager applies and the reason a launcher can read a nested library in place. A
 * compressed nested library is available too, because jarproof has to report that shape rather than
 * fail on it.
 */
final class BootLayoutFixture {
    static final String CLASSES_ROOT = "BOOT-INF/classes/";
    static final String LIBRARY_DIRECTORY = "BOOT-INF/lib/";
    static final String INDEX_PATH = "BOOT-INF/classpath.idx";
    static final String WAR_CLASSES_ROOT = "WEB-INF/classes/";
    static final String WAR_LIBRARY_DIRECTORY = "WEB-INF/lib/";
    static final String DECLARED_CLASSES = "Spring-Boot-Classes";
    static final String DECLARED_LIBRARIES = "Spring-Boot-Lib";

    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private final Set<String> stored = new LinkedHashSet<>();

    private BootLayoutFixture() {
    }

    /** Starts an empty application archive. */
    static BootLayoutFixture archive() {
        return new BootLayoutFixture();
    }

    /** Adds one compressed entry, which is every entry except a nested library. */
    BootLayoutFixture with(String entryName, byte[] content) {
        entries.put(entryName, content);
        return this;
    }

    /** Adds one nested library, stored, which is what a launcher can read in place. */
    BootLayoutFixture withLibrary(String entryName, Map<String, byte[]> content) {
        stored.add(entryName);
        return with(entryName, jarBytes(content));
    }

    /** Adds one nested library from archive bytes crafted elsewhere, stored as a launcher needs it. */
    BootLayoutFixture withStoredLibrary(String entryName, byte[] content) {
        stored.add(entryName);
        return with(entryName, content);
    }

    /** Adds one nested library the packager compressed instead of storing. */
    BootLayoutFixture withCompressedLibrary(String entryName, Map<String, byte[]> content) {
        return with(entryName, jarBytes(content));
    }

    /** Adds the index that names the libraries in the order a launcher adds them. */
    BootLayoutFixture withIndex(String indexPath, String... libraryPaths) {
        StringBuilder index = new StringBuilder();
        for (String libraryPath : libraryPaths) {
            index.append("- \"").append(libraryPath).append("\"\n");
        }
        return with(indexPath, index.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the archive, entry by entry, in the order the entries were added.
     *
     * @param directory directory to write into
     * @param name archive file name, which may name directories of its own
     * @return the written archive
     */
    Path write(Path directory, String name) {
        Path archive = directory.resolve(name);
        try {
            Files.createDirectories(archive.getParent());
            try (OutputStream file = Files.newOutputStream(archive);
                    ZipOutputStream zip = new ZipOutputStream(file)) {
                writeInto(zip);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return archive;
    }

    /** Returns the same entries as the bytes of a plain archive, for nesting inside another one. */
    static byte[] jarBytes(Map<String, byte[]> content) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : content.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    private void writeInto(ZipOutputStream zip) throws IOException {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            zip.putNextEntry(stored.contains(entry.getKey())
                    ? storedEntry(entry.getKey(), entry.getValue())
                    : new ZipEntry(entry.getKey()));
            zip.write(entry.getValue());
            zip.closeEntry();
        }
    }

    /** A stored entry needs its size and CRC decided before any of its bytes are written. */
    private static ZipEntry storedEntry(String entryName, byte[] content) {
        CRC32 digest = new CRC32();
        digest.update(content);
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(digest.getValue());
        return entry;
    }
}
