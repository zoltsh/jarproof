package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * Crafted archive structure, proving each shape becomes a finding or a refusal and never a crash.
 *
 * <p>An artifact is untrusted input, and the shapes here are the ones a packager cannot produce and a
 * stranger can: bytes appended after the central directory, an upload that stopped early, content that
 * no longer matches what was compressed, one name used twice, names shaped like an escape from the
 * directory nobody extracts to, and a name that is not text in the encoding it claims. Every case
 * asserts the specific outcome -- a named finding code, or the refusal type with the shape of its
 * message -- because "did not crash" is not a contract anyone can rely on.
 *
 * <p>Both commands are run over every corpus that admits both. {@code check} and {@code inspect} read
 * the same bytes through different readers, so a shape that one of them survives is not evidence about
 * the other, and the two answers are frequently different on purpose: an inspection reports what an
 * artifact holds without opening what it carries.
 */
final class AdversarialArchiveShapeTest {
    private static final String ALPHA = "com/acme/adversary/Alpha";
    private static final String BRAVO = "com/acme/adversary/Bravo";
    private static final String ALPHA_ENTRY = ALPHA + ArchiveLayout.CLASS_SUFFIX;
    private static final String TRAVERSAL_ENTRY = "../../etc/Escape" + ArchiveLayout.CLASS_SUFFIX;
    private static final String BACKSLASH_ENTRY = "..\\..\\windows\\Escape" + ArchiveLayout.CLASS_SUFFIX;
    private static final String UNINSPECTABLE = "Cannot inspect this artifact";
    private static final int JAVA_17_LEVEL = 17;
    private static final int INTO_PAYLOAD = 4;

    @TempDir
    Path workspace;

    /**
     * A self-extracting wrapper, or a careless concatenation, leaves bytes after the central directory.
     * The format locates that directory from the end of the file, and both readers are expected to find
     * it anyway rather than to reject an archive every real launcher accepts.
     */
    @Test
    void readsAnArchiveWhoseBytesRunOnPastItsCentralDirectory() {
        Path artifact = write("padded.jar", AdversarialArchives.padded(plainArchive(), "trailing stub bytes"));

        assertEquals(List.of(), codes(artifact));
        assertEquals(List.of(ALPHA), indexed(artifact));
        assertFacts(Jarproof.inspect(artifact), 1, 1);
    }

    /**
     * A copy that stopped early loses the directory that lives at the end of it, so there is nothing to
     * read entry by entry. Each reader refuses with the documented argument failure, naming the artifact
     * in the caller's own terms.
     */
    @Test
    void refusesAnArchiveThatStopsBeforeItsDirectory() {
        byte[] complete = plainArchive();
        Path halved = write("halved.jar", AdversarialArchives.truncated(complete, complete.length / 2));
        Path stub = write("stub.jar", AdversarialArchives.truncated(complete, INTO_PAYLOAD));

        for (Path artifact : List.of(halved, stub)) {
            IllegalArgumentException refused =
                    assertThrows(IllegalArgumentException.class, () -> codes(artifact), artifact::toString);
            assertEquals(ArchiveManifest.UNREADABLE + artifact, refused.getMessage());
            IllegalArgumentException uninspectable =
                    assertThrows(IllegalArgumentException.class, () -> Jarproof.inspect(artifact));
            assertTrue(uninspectable.getMessage().startsWith(UNINSPECTABLE), uninspectable::getMessage);
            assertTrue(uninspectable.getMessage().endsWith(artifact.toString()), uninspectable::getMessage);
        }
    }

    /**
     * Content damaged behind an intact directory is the case no checksum catches here: neither
     * {@code ZipFile} nor this engine verifies a CRC on the way in, so the bytes arrive, are not a class
     * file, and become the finding that says so. An inspection counts the entry it was promised and
     * records no bytecode level for it, which is the same answer stated in the terms of a fact table.
     */
    @Test
    void reportsAnEntryWhoseContentNoLongerMatchesWhatWasCompressed() {
        Path artifact = write("rotten.jar", AdversarialArchives.corruptedPayload(plainArchive(), INTO_PAYLOAD));

        assertEquals(List.of("JP3004"), codes(artifact));
        assertEquals(List.of(), indexed(artifact));
        ArtifactSummary summary = Jarproof.inspect(artifact);
        assertEquals(1, summary.entryCount());
        assertEquals(1, summary.classCount(), "the entry is still named like a class");
        assertEquals(List.of(), summary.bytecodeLevels(), "and no longer opens like one");
    }

    /**
     * A name written by a tool that never agreed on an encoding, with the flag that would have announced
     * UTF-8 left clear. Measured, then pinned: the platform reader rejects the central directory outright
     * rather than handing back a name with replacement characters in it, so this is a refusal at open and
     * never a finding. Nothing in the engine has to decide what such a name means, which is the point.
     */
    @Test
    void refusesAnEntryNameThatIsNotTextInTheEncodingItClaims() {
        byte[] declared = ALPHA_ENTRY.getBytes(StandardCharsets.UTF_8);
        byte[] mangled = declared.clone();
        mangled[0] = (byte) 0xFF;
        Path artifact = write("mojibake.jar", AdversarialArchives.replaced(plainArchive(), declared, mangled, 2));

        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> codes(artifact));
        assertEquals(ArchiveManifest.UNREADABLE + artifact, refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Jarproof.inspect(artifact));
    }

    /**
     * One archive, one name, two entries under it. A runtime reaches exactly one class through such a
     * name and so does the engine: the second copy is shadowed inside the archive rather than reported
     * as a duplicate, because duplication is a fact about two artifacts competing and this is one
     * artifact contradicting itself.
     *
     * <p>Which of the two copies the platform reader hands back is its business, so it is deliberately
     * not asserted -- only that the answer is one class, under the name written twice, and the same on
     * every run. An inspection counts both entries, because both are entries.
     */
    @Test
    void indexesOneClassForAnArchiveThatNamesTheSameEntryTwice() {
        Path artifact = duplicateNamedEntries();

        assertEquals(List.of(), codes(artifact));
        assertEquals(List.of(ALPHA), indexed(artifact));
        assertEquals(digests(artifact), digests(artifact), "the shadowed copy must not vary between runs");
        assertFacts(Jarproof.inspect(artifact), 2, 2);
    }

    /**
     * Names shaped like a path escape, in both separator conventions. Nothing here extracts anything, so
     * there is no directory to escape from and no name to sanitise: an entry name is an index key and a
     * piece of display text, and both stay exactly the bytes the archive carried. The assertion that
     * matters as much as the names is the last one -- the run wrote nothing next to the archive it read.
     */
    @Test
    void keepsNamesShapedLikeAnEscapeExactlyAsTheArchiveWroteThem() throws IOException {
        Path artifact = traversalNamedEntries();

        assertEquals(List.of(), codes(artifact));
        assertEquals(
                List.of(ArchiveLayout.internalName(TRAVERSAL_ENTRY), ArchiveLayout.internalName(BACKSLASH_ENTRY)),
                indexed(artifact).stream().sorted().toList());
        assertFacts(Jarproof.inspect(artifact), 2, 2);
        assertEquals(List.of(artifact), filesUnder(workspace), "reading an archive creates no files");
    }

    /** An entry named like a class that holds no bytes at all is unparseable, not unreadable. */
    @Test
    void reportsAnEmptyClassEntryAsUnparseable() {
        Path artifact = write("hollow.jar", AdversarialArchives.archive(
                EngineFixture.entries(ALPHA_ENTRY, new byte[0]), Set.of()));

        assertEquals(List.of("JP3004"), codes(artifact));
        assertEquals(List.of(), indexed(artifact));
        ArtifactSummary summary = Jarproof.inspect(artifact);
        assertEquals(1, summary.classCount());
        assertEquals(List.of(), summary.bytecodeLevels());
    }

    private static void assertFacts(ArtifactSummary summary, int entries, int classes) {
        assertEquals(entries, summary.entryCount());
        assertEquals(classes, summary.classCount());
        assertEquals(0, summary.nestedArchiveCount());
        assertEquals(List.of(EngineFixture.JAVA_17_MAJOR + ":" + classes), summary.bytecodeLevels());
    }

    /** One entry holding one class, written the ordinary way, before anything is done to it. */
    private static byte[] plainArchive() {
        return AdversarialArchives.archive(
                EngineFixture.entries(ALPHA_ENTRY, EngineFixture.classFile(ALPHA)), Set.of());
    }

    /**
     * Two entries whose names are the same length, with the second name overwritten by the first
     * everywhere the archive records it. {@link java.util.zip.ZipOutputStream} refuses to write a
     * duplicate name, so the only way to hold one is to write two and rename one afterwards.
     */
    private Path duplicateNamedEntries() {
        Map<String, byte[]> entries = EngineFixture.entries(ALPHA_ENTRY, EngineFixture.classFile(ALPHA));
        entries.put(BRAVO + ArchiveLayout.CLASS_SUFFIX, EngineFixture.classFile(BRAVO));
        byte[] archive = AdversarialArchives.archive(entries, Set.of());
        return write("twins.jar", AdversarialArchives.replaced(
                archive,
                (BRAVO + ArchiveLayout.CLASS_SUFFIX).getBytes(StandardCharsets.UTF_8),
                ALPHA_ENTRY.getBytes(StandardCharsets.UTF_8),
                2));
    }

    private Path traversalNamedEntries() {
        Map<String, byte[]> entries = EngineFixture.entries(TRAVERSAL_ENTRY, EngineFixture.classFile(ALPHA));
        entries.put(BACKSLASH_ENTRY, EngineFixture.classFile(BRAVO));
        return write("escape.jar", AdversarialArchives.archive(entries, Set.of()));
    }

    private Path write(String name, byte[] content) {
        return AdversarialArchives.write(workspace, name, content);
    }

    private static List<String> codes(Path artifact) {
        return EngineFixture.codes(EngineFixture.verify(List.of(artifact), List.of(), JAVA_17_LEVEL));
    }

    private static List<String> indexed(Path artifact) {
        return classes(artifact).map(IndexedClass::internalName).toList();
    }

    private static List<String> digests(Path artifact) {
        return classes(artifact).map(IndexedClass::digest).toList();
    }

    private static Stream<IndexedClass> classes(Path artifact) {
        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(List.of(artifact), List.of(), JAVA_17_LEVEL), new ResourceBudget());
        return catalog.artifacts().stream().flatMap(one -> one.classes().stream());
    }

    private static List<Path> filesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
