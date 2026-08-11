package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;

/**
 * A directory is not content, wherever it is written down.
 *
 * <p>An archive records its folders as entries of their own, and a class directory has real folders on
 * disk, so every reader has to tell a container apart from a thing inside one. The distinction has no
 * consequence for a class file — a name ending in a separator is no class name — which is why it can be
 * dropped without any test noticing. It has a consequence for every other reader: a folder called
 * {@code META-INF/versions/9/} looks like a release directory, a folder under {@code META-INF/services/}
 * looks like a configuration file, and a folder called {@code BOOT-INF/classes/} looks like a launcher
 * layout. Each of those mistakes invents a diagnostic about an artifact that is perfectly ordinary.
 */
final class DirectoryEntryTest {
    private static final String INTERNAL_NAME = "com/acme/orders/Widget";
    private static final String CLASS_ENTRY = INTERNAL_NAME + ".class";
    private static final String VERSIONS_DIRECTORY = "META-INF/versions/9/";
    private static final String SERVICES_DIRECTORY = "META-INF/services/";
    private static final String SERVICE_NAME = "com.acme.codec.Codec";
    private static final String VERSIONED_LAYOUT = "JP3003";
    private static final String EMPTY_ROOT = "JP3007";

    @TempDir
    Path workspace;

    /** A folder entry beside the classes says nothing about releases, so no release layout is reported. */
    @Test
    void readsPastABareVersionsDirectoryEntryInAnArchive() {
        Map<String, byte[]> entries = EngineFixture.entries(CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME));
        entries.put(VERSIONS_DIRECTORY, new byte[0]);
        Path archive = EngineFixture.jar(workspace, "folders.jar", entries);

        assertEquals(Optional.empty(), coded(archive, VERSIONED_LAYOUT));
    }

    /** The same folder on disk is the same non-statement about releases. */
    @Test
    void readsPastAnEmptyVersionsDirectoryInAClassDirectory() throws IOException {
        Path directory = classDirectory("empty-versions");
        Files.createDirectories(directory.resolve(VERSIONS_DIRECTORY));

        assertEquals(Optional.empty(), coded(directory, VERSIONED_LAYOUT));
    }

    /**
     * A class directory that really does hold versioned entries is reported like an archive that does.
     * A class directory carries no manifest, so it can never declare the attribute that would make the
     * layout legitimate, and the release directory a launcher will never read is the finding.
     */
    @Test
    void reportsVersionedEntriesInAClassDirectoryThatCanDeclareNoMultiReleaseAttribute() throws IOException {
        Path directory = classDirectory("versioned");
        Path versioned = directory.resolve(VERSIONS_DIRECTORY + CLASS_ENTRY);
        Files.createDirectories(versioned.getParent());
        Files.write(versioned, EngineFixture.classFile(INTERNAL_NAME));

        assertEquals(Optional.empty(), coded(directory, EMPTY_ROOT));
        assertEquals(List.of(VERSIONED_LAYOUT), EngineFixture.codes(findings(directory)));
    }

    /**
     * A folder named the way a configuration file is named is still a folder. Reading it as one would
     * fail the whole run on an artifact whose only oddity is an empty directory.
     */
    @Test
    void readsPastASubdirectoryOfTheServicesDirectoryInAClassDirectory() throws IOException {
        Path directory = classDirectory("services");
        Files.createDirectories(directory.resolve(SERVICES_DIRECTORY + SERVICE_NAME));

        assertEquals(List.of(), EngineFixture.codes(findings(directory)));
    }

    /**
     * An archive that merely carries the folder a launcher would put classes in is an ordinary archive.
     * Reading it as a launcher layout would split one position into a nested root and a host archive, and
     * the nested root — a folder with nothing in it — would then be reported as contributing nothing.
     */
    @Test
    void readsAnArchiveCarryingABareLauncherClassesFolderAsAnOrdinaryArchive() {
        Path application = BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT, new byte[0])
                .with(CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME))
                .write(workspace, "bare-classes.jar");

        assertEquals(List.of(), EngineFixture.codes(findings(application)));
    }

    private Path classDirectory(String name) {
        return EngineFixture.classDirectory(workspace, name,
                EngineFixture.entries(CLASS_ENTRY, EngineFixture.classFile(INTERNAL_NAME)));
    }

    private static Optional<Finding> coded(Path artifact, String code) {
        return EngineFixture.coded(findings(artifact), code);
    }

    private static List<Finding> findings(Path artifact) {
        return EngineFixture.verify(List.of(artifact), List.of(), 17);
    }
}
