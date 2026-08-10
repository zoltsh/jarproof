package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * A library nested inside an application archive whose recorded checksum disagrees with its bytes.
 *
 * <p>This is the one adversarial shape whose outcome had to be measured rather than reasoned about, and
 * the reason is the reader. A nested library is stored, not compressed, so a launcher can address it in
 * place; jarproof reads it out of the outer archive's bytes and walks it as a stream, and a stream is
 * the one archive reader in the platform that verifies a CRC-32 as it goes. So the lie is caught -- not
 * by jarproof, by the reader underneath it -- during the walk that enumerates the library, before any
 * class of it has been selected.
 *
 * <p><strong>Measured, then pinned.</strong> {@code check} refuses with {@link UncheckedIOException}
 * carrying the reader's own complaint, which names both checksums. That is a third refusal channel
 * beside the two {@link Jarproof} documents by name, and it is the one the repository already relies on
 * wherever a read fails after the classpath was settled; the CLI catches it with the other two and ends
 * the run with the invocation status and a single line, so it is a refusal at the product surface and
 * never an uncaught crash. The alternative -- translating it to an argument failure -- would be an
 * engine change and a contract decision, not a test's to make.
 *
 * <p>{@code inspect} answers the same corpus normally, and that difference is not an inconsistency: an
 * inspection counts the archives an artifact carries without opening any of them, so a library it never
 * reads cannot lie to it.
 */
final class AdversarialNestedLibraryTest {
    private static final String HOSTED = "com/acme/adversary/Hosted";
    private static final String CARRIED = "com/acme/carried/Carried";
    private static final String LIBRARY_PATH = BootLayoutFixture.LIBRARY_DIRECTORY + "lying.jar";
    private static final String CRC_COMPLAINT = "invalid entry CRC";
    private static final int JAVA_17_LEVEL = 17;
    private static final int CORRUPTED_BYTE = 0;

    @TempDir
    Path workspace;

    @Test
    void refusesANestedLibraryWhoseRecordedChecksumDisagreesWithItsBytes() {
        Path application = applicationCarryingALie();

        UncheckedIOException refused = assertThrows(
                UncheckedIOException.class,
                () -> EngineFixture.verify(List.of(application), List.of(), JAVA_17_LEVEL));

        assertTrue(refused.getMessage().contains(CRC_COMPLAINT), refused::getMessage);
    }

    @Test
    void inspectsTheSameApplicationWithoutOpeningTheLibraryThatLies() {
        ArtifactSummary summary = Jarproof.inspect(applicationCarryingALie());

        assertEquals(2, summary.entryCount());
        assertEquals(1, summary.nestedArchiveCount());
        assertEquals(1, summary.classCount(), "a class inside the nested library is not an entry of this one");
        assertEquals(List.of(EngineFixture.JAVA_17_MAJOR + ":1"), summary.bytecodeLevels());
    }

    /**
     * The honest half of the same corpus: the identical layout with the checksum left alone, proving the
     * refusal above is about the lie and not about the shape it was told in.
     */
    @Test
    void readsTheSameApplicationWhenTheChecksumTellsTheTruth() {
        Path application = application(honestLibrary());

        assertEquals(List.of(), EngineFixture.codes(
                EngineFixture.verify(List.of(application), List.of(), JAVA_17_LEVEL)));
    }

    private Path applicationCarryingALie() {
        byte[] carried = EngineFixture.classFile(CARRIED);
        byte[] declared = AdversarialArchives.recordedCrc(carried);
        byte[] lie = declared.clone();
        lie[CORRUPTED_BYTE] = (byte) (lie[CORRUPTED_BYTE] ^ 0x5A);
        return application(AdversarialArchives.replaced(honestLibrary(), declared, lie, 2));
    }

    private static byte[] honestLibrary() {
        return AdversarialArchives.archive(
                EngineFixture.entries(CARRIED + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(CARRIED)),
                Set.of());
    }

    /** An application archive carrying one class of its own and one stored library. */
    private Path application(byte[] library) {
        Map<String, byte[]> entries = EngineFixture.entries(
                BootLayoutFixture.CLASSES_ROOT + HOSTED + ArchiveLayout.CLASS_SUFFIX,
                EngineFixture.classFile(HOSTED));
        entries.put(LIBRARY_PATH, library);
        return AdversarialArchives.write(
                workspace, "host.jar", AdversarialArchives.archive(entries, Set.of(LIBRARY_PATH)));
    }
}
