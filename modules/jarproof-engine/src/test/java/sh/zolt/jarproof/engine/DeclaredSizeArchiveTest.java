package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A declared size is a claim an archive makes about an entry, never a measurement of one.
 *
 * <p>Nothing in {@code java.util.zip} compares the size an entry declares with the bytes behind it, so a
 * reader that believed the claim would refuse artifacts nobody sent and a reader that ignored it would read
 * whatever arrived. Every entry is therefore measured twice — once against what it claims, before a byte is
 * expanded, and once against what it actually produced — and these are the two halves of that pair, asserted
 * from both commands that read entries. Each archive here is assembled as bytes and patched afterwards
 * because no packager can produce either shape: one declares megabytes it does not carry, the other carries
 * megabytes it does not declare.
 */
final class DeclaredSizeArchiveTest {
    private static final String INTERNAL_NAME = "com/acme/orders/Widget";
    private static final String CLASS_ENTRY = INTERNAL_NAME + ArchiveLayout.CLASS_SUFFIX;
    private static final String SERVICE_ENTRY = ServiceDeclaration.RESOURCE_PREFIX + "com.acme.orders.Codec";
    private static final String PROVIDER_LINE = "com.acme.orders.PlainCodec\n";
    private static final String STORED_TEXT = "the few bytes this entry really stores";
    private static final long BEYOND_THE_CEILING = ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1L;
    private static final int UNDERSTATED_BYTES = 8;

    @TempDir
    Path workspace;

    /**
     * A class entry claiming more bytes than a class file may occupy is refused on the claim alone, which
     * is what makes the refusal free: the bytes it would have taken to read are never read, and the entry
     * is refused for what it says about itself rather than for what turns out to be inside it.
     */
    @Test
    void refusesAClassEntryThatDeclaresMoreBytesThanAClassFileMayOccupy() {
        Path artifact = overstating("overstated.jar", CLASS_ENTRY, STORED_TEXT.getBytes(StandardCharsets.UTF_8));

        assertNamesTheClassFileCeiling(assertThrows(IllegalStateException.class, () -> codes(artifact)), CLASS_ENTRY);
        assertNamesTheClassFileCeiling(
                assertThrows(IllegalStateException.class, () -> Jarproof.inspect(artifact)), CLASS_ENTRY);
    }

    /** A service configuration file is read whole through the same budget, so it answers to the same claim. */
    @Test
    void refusesAServiceFileThatDeclaresMoreBytesThanAClassFileMayOccupy() {
        Path artifact = overstating("overstated-service.jar", SERVICE_ENTRY, providerLines());

        assertNamesTheClassFileCeiling(
                assertThrows(IllegalStateException.class, () -> codes(artifact)), SERVICE_ENTRY);
    }

    /**
     * The other direction, and the reason the ceiling is applied a second time: an entry that declares
     * eight bytes and inflates to more than a class file may occupy is refused by what arrived. The claim
     * let it past the first check, so a reader with only that check would have held all of it in memory.
     */
    @Test
    void refusesAClassEntryThatDeclaresLittleAndDeliversMoreThanAClassFileMayOccupy() {
        Path artifact = understating("understated.jar", CLASS_ENTRY);

        assertNamesTheClassFileCeiling(assertThrows(IllegalStateException.class, () -> codes(artifact)), CLASS_ENTRY);
    }

    /** The same understatement in a configuration file, which is read whole rather than parsed lazily. */
    @Test
    void refusesAServiceFileThatDeclaresLittleAndDeliversMoreThanAClassFileMayOccupy() {
        Path artifact = understating("understated-service.jar", SERVICE_ENTRY);

        assertNamesTheClassFileCeiling(
                assertThrows(IllegalStateException.class, () -> codes(artifact)), SERVICE_ENTRY);
    }

    private static void assertNamesTheClassFileCeiling(IllegalStateException refused, String entryName) {
        assertTrue(refused.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES)),
                refused::getMessage);
        assertTrue(refused.getMessage().contains(entryName), refused::getMessage);
    }

    /** An archive whose one stored entry declares a size larger than the ceiling and stores a handful. */
    private Path overstating(String name, String entryName, byte[] content) {
        byte[] archive = AdversarialArchives.archive(
                EngineFixture.entries(entryName, content), Set.of(entryName));
        return AdversarialArchives.write(workspace, name,
                AdversarialArchives.overstating(archive, content.length, BEYOND_THE_CEILING));
    }

    /** An archive whose one deflated entry declares eight bytes and expands past the ceiling. */
    private Path understating(String name, String entryName) {
        byte[] content = new byte[ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1];
        byte[] archive = AdversarialArchives.archive(EngineFixture.entries(entryName, content), Set.of());
        return AdversarialArchives.write(workspace, name,
                AdversarialArchives.understating(archive, content.length, UNDERSTATED_BYTES));
    }

    private static byte[] providerLines() {
        return PROVIDER_LINE.getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> codes(Path artifact) {
        return EngineFixture.codes(EngineFixture.verify(List.of(artifact), List.of(), 17));
    }
}
