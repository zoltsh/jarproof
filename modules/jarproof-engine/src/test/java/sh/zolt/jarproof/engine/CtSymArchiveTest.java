package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class CtSymArchiveTest {
    private static final int PUBLIC_SUPER = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
    private static final int HIGHEST_CODED_RELEASE = 35;
    private static final String SHARED = "com/example/Shared";
    private static final String ALPHA_ENTRY = "H/example.alpha/com/example/Shared.sig";
    private static final String ZEBRA_ENTRY = "H/example.zebra/com/example/Shared.sig";

    @TempDir
    Path workspace;

    @Test
    void readsTheToolchainArchiveForABundledRelease() {
        JdkSymbolCatalog catalog = supplied(JdkSymbolResourceGenerator.toolchainCtSym()).catalog(17);

        assertEquals(17, catalog.javaRelease());
        assertTrue(catalog.classCount() > 4000, () -> "Only " + catalog.classCount() + " classes");
        assertEquals(Optional.of("java.base"), catalog.owningModule("java/lang/Object"));
    }

    @Test
    void theToolchainArchiveCoversTheReleasesItDeclares() {
        Set<Integer> releases = supplied(JdkSymbolResourceGenerator.toolchainCtSym()).releases();

        assertTrue(releases.contains(8), () -> "Missing Java 8 in " + releases);
        assertTrue(releases.contains(17), () -> "Missing Java 17 in " + releases);
    }

    @Test
    void oneEntryServesEveryReleaseItsCodeNames() throws IOException {
        Path archive = archive(Map.of(
                "89AL/example.base/com/example/Shared.sig", nestedClass("com/example/Shared"),
                "89AL/example.base/module-info.sig", nestedClass("module-info")));

        for (int release : List.of(8, 9, 10, 21)) {
            JdkSymbolCatalog catalog = supplied(archive).catalog(release);

            assertEquals(1, catalog.classCount(), () -> "Java " + release);
            assertEquals(Optional.of("example.base"), catalog.owningModule("com/example/Shared"));
        }
    }

    @Test
    void declarationsSurviveTheRoundTripThroughAsm() throws IOException {
        Path archive = archive(Map.of("H/example.base/com/example/Shared.sig", nestedClass("com/example/Shared")));

        ClassShape shape = supplied(archive).catalog(17)
                .classShape("com/example/Shared")
                .orElseThrow();

        assertEquals(PUBLIC_SUPER, shape.accessFlags());
        assertEquals(Optional.of("java/lang/Object"), shape.superInternalName());
        assertEquals(List.of("java/io/Serializable"), shape.interfaceInternalNames());
        assertEquals(Optional.of("com/example/Outer"), shape.nestHostInternalName());
        assertEquals(
                List.of(
                        new MemberShape("<init>", "()V", Opcodes.ACC_PUBLIC),
                        new MemberShape("count", "I", Opcodes.ACC_PRIVATE)),
                shape.members());
    }

    @Test
    void nestMembersAreSortedByName() throws IOException {
        Path archive = archive(Map.of("H/example.base/com/example/Outer.sig", hostClass()));

        ClassShape shape = supplied(archive).catalog(17).classShape("com/example/Outer").orElseThrow();

        assertEquals(
                List.of("com/example/Outer$Alpha", "com/example/Outer$Beta", "com/example/Outer$Gamma"),
                shape.nestMemberInternalNames());
    }

    @Test
    void aReleaseWithoutSignaturesNamesWhatTheArchiveCovers() throws IOException {
        Path archive = archive(Map.of("H/example.base/com/example/Shared.sig", nestedClass("com/example/Shared")));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> supplied(archive).catalog(21));

        assertTrue(failure.getMessage().contains("Java 21"), failure::getMessage);
        assertTrue(failure.getMessage().contains("[17]"), failure::getMessage);
    }

    @Test
    void releasesOutsideTheCodedRangeAreRejected() {
        Path archive = JdkSymbolResourceGenerator.toolchainCtSym();

        assertThrows(IllegalArgumentException.class, () -> supplied(archive).catalog(7));
        assertThrows(IllegalArgumentException.class, () -> supplied(archive).catalog(36));
    }

    @Test
    void signaturesOutsideAReleaseDirectoryAreIgnored() throws IOException {
        Path archive = archive(Map.of(
                "Loose.sig", nestedClass("Loose"),
                "H/example.base/com/example/Shared.sig", nestedClass("com/example/Shared")));

        CtSymArchive ctSym = supplied(archive);

        assertEquals(1, ctSym.catalog(17).classCount());
        assertEquals(Set.of(17), ctSym.releases());
    }

    /**
     * A class two modules declare at one release is owned by one of them, and which one is a fact about
     * the archive rather than about the order it happens to have been written in. Applicable entries are
     * sorted before they are parsed, so both writings answer identically.
     */
    @Test
    void answersWithTheSameOwningModuleWhateverOrderTheEntriesWereWrittenIn() throws IOException {
        Path ordered = archive("ordered-ct.sym", twoModules(false));
        Path reversed = archive("reversed-ct.sym", twoModules(true));

        Optional<String> first = supplied(ordered).catalog(17).owningModule(SHARED);

        assertEquals(first, supplied(reversed).catalog(17).owningModule(SHARED));
        assertTrue(first.isPresent(), "a class declared twice is still owned once");
    }

    /**
     * A release the archive names only in a {@code system-modules} entry is not a release it carries
     * signatures for. That is exactly how a JDK's own release appears in its own {@code ct.sym}, and
     * counting it would offer a release whose declarations are somewhere else entirely.
     */
    @Test
    void countsOnlyTheReleasesItCarriesSignaturesFor() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("H/example.base/com/example/Shared.sig", nestedClass(SHARED));
        entries.put("L/system-modules", "example.base\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(Set.of(17), supplied(archive("own-release-ct.sym", entries)).releases());
    }

    /** The highest release the code range covers has a code, so an archive may carry signatures for it. */
    @Test
    void readsSignaturesForTheHighestCodedRelease() throws IOException {
        Path archive = archive("highest-ct.sym",
                Map.of("Z/example.base/com/example/Shared.sig", nestedClass(SHARED)));

        assertEquals(1, supplied(archive).catalog(HIGHEST_CODED_RELEASE).classCount());
    }

    @Test
    void anEntryWithoutAModuleSegmentIsRejected() throws IOException {
        Path archive = archive(Map.of("H/Stray.sig", nestedClass("Stray")));

        assertThrows(IllegalStateException.class, () -> supplied(archive).catalog(17));
    }

    @Test
    void aFileThatIsNotAZipIsReportedWithItsPath() throws IOException {
        Path notAnArchive = Files.writeString(workspace.resolve("ct.sym"), "plain text");

        UncheckedIOException failure =
                assertThrows(UncheckedIOException.class, () -> supplied(notAnArchive).catalog(17));

        assertTrue(failure.getMessage().contains(notAnArchive.toString()), failure::getMessage);
        assertThrows(UncheckedIOException.class, () -> supplied(notAnArchive).releases());
    }

    @Test
    void aMissingArchiveIsReported() {
        Path absent = workspace.resolve("absent.sym");

        assertThrows(UncheckedIOException.class, () -> supplied(absent).catalog(17));
    }

    /** Reads an archive the way a caller supplied it: the handle is its own text, resolved once. */
    private static CtSymArchive supplied(Path archive) {
        return new CtSymArchive(archive.toAbsolutePath().normalize(), archive.toString());
    }

    /** The same two entries, written in either order, so the sort has something to disagree with. */
    private static Map<String, byte[]> twoModules(boolean reversed) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(reversed ? ZEBRA_ENTRY : ALPHA_ENTRY, nestedClass(SHARED));
        entries.put(reversed ? ALPHA_ENTRY : ZEBRA_ENTRY, nestedClass(SHARED));
        return entries;
    }

    private Path archive(Map<String, byte[]> entries) throws IOException {
        return archive("synthetic-ct.sym", entries);
    }

    private Path archive(String name, Map<String, byte[]> entries) throws IOException {
        Path archive = workspace.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("H/system-modules"));
            zip.write("example.base\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }

    private static byte[] nestedClass(String internalName) {
        ClassWriter writer = newClass(internalName);
        writer.visitNestHost("com/example/Outer");
        writer.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] hostClass() {
        ClassWriter writer = newClass("com/example/Outer");
        writer.visitNestMember("com/example/Outer$Gamma");
        writer.visitNestMember("com/example/Outer$Alpha");
        writer.visitNestMember("com/example/Outer$Beta");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter newClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                PUBLIC_SUPER,
                internalName,
                null,
                "java/lang/Object",
                new String[] {"java/io/Serializable"});
        return writer;
    }
}
