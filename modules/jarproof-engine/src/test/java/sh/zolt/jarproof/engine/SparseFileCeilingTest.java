package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A file's length is a claim as well, and it costs nothing at all to make one.
 *
 * <p>A class directory is addressed by path rather than through an archive's headers, so the size its
 * reader consults is the length the filesystem reports — and a file written as a hole reports a length it
 * does not occupy. Thirty-two megabytes of nothing is one block on disk and one seek to write, which makes
 * it the cheapest form of the very input these ceilings exist for. Each ceiling is applied to that length
 * before a byte of the file is read, so the refusal costs the run as little as the file cost its sender.
 */
final class SparseFileCeilingTest {
    private static final String INTERNAL_NAME = "com/acme/orders/Widget";
    private static final String CLASS_ENTRY = INTERNAL_NAME + ArchiveLayout.CLASS_SUFFIX;
    private static final String SERVICE_ENTRY = ServiceDeclaration.RESOURCE_PREFIX + "com.acme.orders.Codec";

    @TempDir
    Path workspace;

    /**
     * A class file longer than a class file may be is refused by both commands that read one, on its
     * length alone: neither the verifier's whole read of it nor the inspector's read of its first eight
     * bytes happens at all.
     */
    @Test
    void refusesAClassFileLongerThanAClassFileMayOccupy() throws IOException {
        Path directory = holdingAHole(CLASS_ENTRY);

        assertNamesTheClassFileCeiling(assertThrows(IllegalStateException.class,
                () -> EngineFixture.verify(List.of(directory), List.of(), 17)), CLASS_ENTRY);
        assertNamesTheClassFileCeiling(
                assertThrows(IllegalStateException.class, () -> Jarproof.inspect(directory)), CLASS_ENTRY);
    }

    /**
     * A service configuration file in a class directory is read whole, so it answers to the ceiling on a
     * whole read — the same one a class file answers to, because both are read the same way.
     */
    @Test
    void refusesAServiceFileLongerThanAClassFileMayOccupy() throws IOException {
        Path directory = holdingAHole(SERVICE_ENTRY);

        assertNamesTheClassFileCeiling(assertThrows(IllegalStateException.class,
                () -> EngineFixture.verify(List.of(directory), List.of(), 17)), SERVICE_ENTRY);
    }

    private static void assertNamesTheClassFileCeiling(IllegalStateException refused, String entryName) {
        assertTrue(refused.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES)),
                refused::getMessage);
        assertTrue(refused.getMessage().contains(entryName), refused::getMessage);
    }

    /**
     * A class directory holding one file whose length is a hole: nothing is written but the last byte, and
     * the filesystem reports every byte before it.
     */
    private Path holdingAHole(String entryName) throws IOException {
        Path directory = EngineFixture.classDirectory(workspace, "classes", EngineFixture.entries());
        Path file = directory.resolve(entryName);
        Files.createDirectories(file.getParent());
        try (SeekableByteChannel channel = Files.newByteChannel(
                file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES);
            channel.write(ByteBuffer.wrap(new byte[1]));
        }
        return directory;
    }
}
