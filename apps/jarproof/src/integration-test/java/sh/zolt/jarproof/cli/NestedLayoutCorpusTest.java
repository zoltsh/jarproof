package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CorpusCheck.Reported;

/**
 * The broken pair from the corpus, repackaged the way a fat JAR ships it.
 *
 * <p>Nothing about the bytecode changes: the same consumer that calls a method the same library no
 * longer declares is moved into the classes root of one application archive, and that library is stored
 * beside it as a nested library. A launcher would build a classpath out of those two areas, so jarproof
 * has to find the same break through them — with no extra flags, and reporting positions inside the
 * archive rather than the archive as one opaque file.
 *
 * <p>The archive is assembled here rather than added to the fixture corpus because the corpus members
 * are compiled Java and this shape is a packaging decision applied to them. Storing the nested library
 * uncompressed is what a launcher-aware packager does, and doing the same here keeps the assertion about
 * the linkage break instead of about the packaging.
 */
final class NestedLayoutCorpusTest {
    private static final String CONSUMER = "missing-method-consumer";
    private static final String BROKEN_API = "missing-method-api-v2";
    private static final String CLASSES_ROOT = "BOOT-INF/classes/";
    private static final String NESTED_LIBRARY = "BOOT-INF/lib/" + BROKEN_API + ".jar";
    private static final String INDEX_PATH = "BOOT-INF/classpath.idx";
    private static final String ARCHIVE = "orders-service.jar";
    private static final String SUBJECT =
            "sh/zolt/jarproof/fixtures/missingmethod/OrderPolicy#describe(Ljava/lang/String;I)Ljava/lang/String;";

    @TempDir
    Path workspace;

    @Test
    void predictsTheSameErrorThroughAnApplicationArchiveThatCarriesItsDependencies() {
        Path application = fatJar();

        CorpusCheck check = CorpusCheck.reporting(
                workspace.resolve("nested.json"),
                List.of(CorpusCommand.APPLICATION, application.toString()));

        assertEquals(
                List.of(new Reported(
                        "JP1003",
                        "error",
                        "NoSuchMethodError",
                        FixtureCorpus.relativePath(application) + "!/BOOT-INF/classes",
                        SUBJECT)),
                check.findings(),
                check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    /** One application archive: the consumer under the classes root, the broken library nested beside it. */
    private Path fatJar() {
        Path archive = workspace.resolve(ARCHIVE);
        try (OutputStream file = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry(INDEX_PATH));
            zip.write(("- \"" + NESTED_LIBRARY + "\"\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            unpackInto(zip, FixtureCorpus.jar(CONSUMER));
            store(zip, NESTED_LIBRARY, Files.readAllBytes(FixtureCorpus.jar(BROKEN_API)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return archive;
    }

    /** Copies one packaged member under the classes root, which is where an application's own classes live. */
    private static void unpackInto(ZipOutputStream zip, Path member) throws IOException {
        try (ZipFile opened = new ZipFile(member.toFile())) {
            for (String entryName : opened.stream().map(ZipEntry::getName).sorted().toList()) {
                copy(zip, opened, entryName);
            }
        }
    }

    private static void copy(ZipOutputStream zip, ZipFile opened, String entryName) throws IOException {
        ZipEntry entry = opened.getEntry(entryName);
        if (entry.isDirectory()) {
            return;
        }
        zip.putNextEntry(new ZipEntry(CLASSES_ROOT + entryName));
        try (InputStream bytes = opened.getInputStream(entry)) {
            zip.write(bytes.readAllBytes());
        }
        zip.closeEntry();
    }

    /** A stored entry needs its size and CRC decided up front, which is why a launcher can read it in place. */
    private static void store(ZipOutputStream zip, String entryName, byte[] content) throws IOException {
        CRC32 digest = new CRC32();
        digest.update(content);
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(digest.getValue());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }
}
