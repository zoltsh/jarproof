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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.ArtifactSummary;

final class InspectionTest {
    private static final String CODEC = "sh.zolt.jarproof.fixtures.service.Codec";
    private static final String SERVICE_ENTRY = "META-INF/services/" + CODEC;
    private static final String BASE_CLASS = "com/acme/app/Modern.class";

    @TempDir
    Path workspace;

    @Test
    void reportsEveryRawLayoutFactOfAnArchive() {
        ArtifactSummary summary = Jarproof.inspect(multiReleaseArchive());

        assertEquals(5, summary.entryCount());
        assertEquals(3, summary.classCount());
        assertEquals(0, summary.nestedArchiveCount());
        assertEquals(List.of("52:1", "61:1", "65:1"), summary.bytecodeLevels());
        assertEquals(List.of(CODEC), summary.declaredServices());
        assertEquals(List.of(11, 21), summary.multiReleaseVersions());
    }

    @Test
    void countsTheArchivesAnApplicationCarriesWithoutOpeningAnyOfThem() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + BASE_CLASS, EngineFixture.classFile("com/acme/app/Modern"))
                .withLibrary(BootLayoutFixture.LIBRARY_DIRECTORY + "codec.jar",
                        EngineFixture.entries("com/acme/lib/Codec.class", EngineFixture.classFile("com/acme/lib/Codec")))
                .with("BOOT-INF/lib/notes.zip", new byte[0])
                .write(workspace, "fat.jar");

        ArtifactSummary summary = Jarproof.inspect(application);

        assertEquals(3, summary.entryCount());
        assertEquals(2, summary.nestedArchiveCount());
        assertEquals(1, summary.classCount(), "a class inside a nested archive is not an entry of this one");
    }

    @Test
    void keepsTheArtifactPathTheCallerSupplied() {
        Path archive = multiReleaseArchive();

        assertEquals(archive.toString(), Jarproof.inspect(archive).artifact());
    }

    @Test
    void selectsNothingForAnyRelease() {
        ArtifactSummary summary = Jarproof.inspect(multiReleaseArchive());

        assertEquals(3, summary.classCount(), "every copy of a versioned class is a fact about the layout");
    }

    @Test
    void readsAClassDirectoryTheSameWay() {
        Path classes = EngineFixture.classDirectory(workspace, "classes", Map.of(
                BASE_CLASS, EngineFixture.classFile("com/acme/app/Modern"),
                "META-INF/versions/17/" + BASE_CLASS, EngineFixture.classFile("com/acme/app/Modern"),
                SERVICE_ENTRY, providerLines()));

        ArtifactSummary summary = Jarproof.inspect(classes);

        assertEquals(3, summary.entryCount());
        assertEquals(2, summary.classCount());
        assertEquals(List.of("61:2"), summary.bytecodeLevels());
        assertEquals(List.of(CODEC), summary.declaredServices());
        assertEquals(List.of(17), summary.multiReleaseVersions());
    }

    @Test
    void reportsAnArtifactWithNothingInIt() {
        ArtifactSummary summary = Jarproof.inspect(EngineFixture.jar(workspace, "empty.jar", Map.of()));

        assertEquals(0, summary.entryCount());
        assertEquals(0, summary.classCount());
        assertEquals(List.of(), summary.bytecodeLevels());
        assertEquals(List.of(), summary.declaredServices());
        assertEquals(List.of(), summary.multiReleaseVersions());
    }

    @Test
    void countsNoVersionForAnEntryThatOnlyLooksLikeAClass() {
        Path archive = EngineFixture.jar(workspace, "pretender.jar",
                Map.of("com/acme/Prose.class", "not bytecode".getBytes(StandardCharsets.UTF_8)));

        ArtifactSummary summary = Jarproof.inspect(archive);

        assertEquals(1, summary.classCount());
        assertEquals(List.of(), summary.bytecodeLevels());
    }

    @Test
    void ignoresLayoutNamesTheRuntimeWouldIgnoreToo() {
        Path archive = EngineFixture.jar(workspace, "odd.jar", Map.of(
                "META-INF/services/nested/Deep", new byte[0],
                "META-INF/versions/experimental/com/acme/Odd.class", EngineFixture.classFile("com/acme/Odd")));

        ArtifactSummary summary = Jarproof.inspect(archive);

        assertEquals(List.of(), summary.declaredServices());
        assertEquals(List.of(), summary.multiReleaseVersions());
    }

    @Test
    void namesOnlyTheConfigurationFilesTheRuntimeConsults() {
        assertEquals(Optional.empty(), InspectionLayout.serviceName("META-INF/services/"));
        assertEquals(Optional.empty(), InspectionLayout.serviceName("META-INF/MANIFEST.MF"));
        assertEquals(Optional.of(CODEC), InspectionLayout.serviceName(SERVICE_ENTRY));
    }

    @Test
    void readsOnlyTheReleaseDirectoriesThatNameARelease() {
        assertEquals(Optional.of(11), InspectionLayout.multiReleaseVersion("META-INF/versions/11/A.class"));
        assertEquals(Optional.empty(), InspectionLayout.multiReleaseVersion("META-INF/versions/"));
        assertEquals(
                Optional.empty(), InspectionLayout.multiReleaseVersion("META-INF/versions/99999999999/A.class"));
        assertEquals(Optional.empty(), InspectionLayout.multiReleaseVersion(BASE_CLASS));
    }

    @Test
    void readsAMajorVersionOnlyFromAWholeClassFileHeader() {
        byte[] complete = EngineFixture.classFile("com/acme/app/Modern");
        byte[] halfTheMagic = new byte[InspectionLayout.HEADER_BYTES];
        halfTheMagic[0] = complete[0];
        halfTheMagic[1] = complete[1];

        assertEquals(Optional.of(61), InspectionLayout.majorVersion(complete));
        assertEquals(Optional.empty(), InspectionLayout.majorVersion(new byte[] {complete[0], complete[1]}));
        assertEquals(Optional.empty(), InspectionLayout.majorVersion(halfTheMagic));
        assertEquals(Optional.empty(), InspectionLayout.majorVersion(new byte[InspectionLayout.HEADER_BYTES]));
    }

    @Test
    void countsOnlyTheEntriesThatHoldContent() {
        Map<String, byte[]> entries = EngineFixture.entries();
        entries.put("com/acme/", new byte[0]);
        entries.put(BASE_CLASS, EngineFixture.classFile("com/acme/app/Modern"));

        ArtifactSummary summary = Jarproof.inspect(EngineFixture.jar(workspace, "foldered.jar", entries));

        assertEquals(1, summary.entryCount(), "a directory entry is not a file the runtime can read");
        assertEquals(1, summary.classCount());
    }

    @Test
    void rejectsAnArtifactThatCannotBeRead() {
        Path absent = workspace.resolve("absent.jar");

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> Jarproof.inspect(absent));

        assertTrue(failure.getMessage().contains(absent.toString()), failure::getMessage);
    }

    @Test
    void rejectsAFileThatIsNotAnArchive() throws IOException {
        Path notAnArchive = Files.writeString(workspace.resolve("notes.txt"), "plain text");

        assertThrows(IllegalArgumentException.class, () -> Jarproof.inspect(notAnArchive));
    }

    @Test
    void rejectsAMissingArtifact() {
        assertThrows(NullPointerException.class, () -> Jarproof.inspect(null));
    }

    private Path multiReleaseArchive() {
        Map<String, byte[]> entries = EngineFixture.entries();
        entries.put(BASE_CLASS, EngineFixture.classFile("com/acme/app/Modern", Opcodes.V1_8));
        entries.put("META-INF/versions/11/" + BASE_CLASS, EngineFixture.classFile("com/acme/app/Modern"));
        entries.put("META-INF/versions/21/" + BASE_CLASS,
                EngineFixture.classFile("com/acme/app/Modern", Opcodes.V21));
        entries.put(SERVICE_ENTRY, providerLines());
        entries.put("META-INF/NOTICE", "notice".getBytes(StandardCharsets.UTF_8));
        return EngineFixture.jar(workspace, "modern.jar", entries);
    }

    private static byte[] providerLines() {
        return "sh.zolt.jarproof.fixtures.service.PlainCodec\n".getBytes(StandardCharsets.UTF_8);
    }
}
