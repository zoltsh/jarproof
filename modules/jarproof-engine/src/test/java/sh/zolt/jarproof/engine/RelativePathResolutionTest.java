package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactSummary;
import sh.zolt.jarproof.api.Finding;

/**
 * Pins that a relative artifact is read through the same base it was validated against.
 *
 * <p>{@code java.nio.file} resolves a relative path against the {@code user.dir} system property,
 * while {@code java.io} and every {@code ZipFile} constructor resolve one against the process
 * working directory. A launcher that points those two at different places hands the engine one
 * relative text with two meanings — it is validated against {@code user.dir} and, before the fix
 * these tests guard, was opened against the working directory instead. Zolt's own test runner does
 * exactly that, which is how the split was found and why these tests fail without the fix here.
 *
 * <p>Every fixture is therefore built through a relative path so it lands wherever
 * {@code java.nio.file} puts it, which keeps these tests honest on a runner whose two bases agree:
 * they still pass there, they just stop being able to fail.
 *
 * <p>Measured on JDK 21: neither base can be moved from inside a running JVM. Setting
 * {@code user.dir} after startup changes nothing, because {@link Path#toAbsolutePath()} reads the
 * default file system's own directory and {@link java.io.File} a private copy, both captured before
 * {@code main}. The property is moved in one test below only to prove the engine settles a path once
 * and never consults that property again, never to manufacture the split.
 */
final class RelativePathResolutionTest {
    private static final String USER_DIR = "user.dir";
    private static final String SCRATCH_PARENT = "target";
    private static final String SCRATCH_PREFIX = "relative-path-";
    private static final String DUPLICATE = "com/acme/relative/Duplicate";
    private static final String DUPLICATE_ENTRY = DUPLICATE + ArchiveLayout.CLASS_SUFFIX;
    private static final String DECLARES = " declares ";

    @TempDir
    Path elsewhere;

    private Path scratch;

    @BeforeEach
    void createRelativeScratchDirectory() throws IOException {
        Files.createDirectories(Path.of(SCRATCH_PARENT));
        scratch = Files.createTempDirectory(Path.of(SCRATCH_PARENT), SCRATCH_PREFIX);
        assertFalse(scratch.isAbsolute(), "this suite only proves anything about a relative entry");
    }

    @AfterEach
    void deleteRelativeScratchDirectory() throws IOException {
        try (Stream<Path> paths = Files.walk(scratch)) {
            paths.sorted(Comparator.reverseOrder()).forEach(RelativePathResolutionTest::delete);
        }
    }

    @Test
    void readsRelativeArchivesAndReportsTheCallerText() {
        Path application = jar("app.jar");
        Path library = jar("lib.jar");

        List<Finding> findings = EngineFixture.verify(List.of(application), List.of(library), 17);

        Finding duplicate = EngineFixture.required(findings, "JP2001");
        assertEquals(application.toString(), duplicate.artifact().artifact());
        assertEquals(Optional.of(DUPLICATE_ENTRY), duplicate.artifact().classEntry());
        assertEquals(
                List.of(application + DECLARES, library + DECLARES),
                EngineFixture.evidence(duplicate).stream().map(RelativePathResolutionTest::declaringPrefix).toList());
    }

    @Test
    void readsARelativeClassDirectoryAndReportsTheCallerText() {
        Path classes = EngineFixture.classDirectory(
                scratch, "classes", Map.of(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        Path library = jar("lib.jar");

        Finding duplicate = EngineFixture.required(
                EngineFixture.verify(List.of(classes), List.of(library), 17), "JP2001");

        assertEquals(classes.toString(), duplicate.artifact().artifact());
        assertEquals(Optional.of(DUPLICATE_ENTRY), duplicate.artifact().classEntry());
    }

    @Test
    void inspectsARelativeArchiveAndReportsTheCallerText() {
        Path archive = jar("app.jar");

        ArtifactSummary summary = Jarproof.inspect(archive);

        assertEquals(archive.toString(), summary.artifact());
        assertEquals(1, summary.entryCount());
        assertEquals(1, summary.classCount());
    }

    @Test
    void inspectsARelativeClassDirectoryAndReportsTheCallerText() {
        Path classes = EngineFixture.classDirectory(
                scratch, "classes", Map.of(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));

        ArtifactSummary summary = Jarproof.inspect(classes);

        assertEquals(classes.toString(), summary.artifact());
        assertEquals(1, summary.classCount());
        assertEquals(List.of(String.valueOf(EngineFixture.JAVA_17_MAJOR) + ":1"), summary.bytecodeLevels());
    }

    @Test
    void settlesEveryReadHandleOnceWhileTheReportKeepsTheCallerText() {
        Path application = jar("app.jar");
        Path chained = jar("chained.jar");
        Path declaring = chainingJar("declaring.jar", "chained.jar");

        List<ClasspathEntry> entries = ClasspathExpander.expand(
                EngineFixture.request(List.of(application), List.of(declaring), 17)).entries();

        assertEquals(
                List.of(application.toString(), declaring.toString(), chained.toString()),
                entries.stream().map(ClasspathEntry::display).toList());
        assertEquals(
                List.of(handle(application), handle(declaring), handle(chained)),
                entries.stream().map(ClasspathEntry::path).toList());
        assertTrue(entries.stream().allMatch(entry -> entry.path().isAbsolute()), entries::toString);
    }

    @Test
    void settlesAPathBeforeTheCallerCanMoveUserDir() {
        Path archive = jar("app.jar");
        String saved = System.getProperty(USER_DIR);
        try {
            System.setProperty(USER_DIR, elsewhere.toString());

            List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);
            ArtifactSummary summary = Jarproof.inspect(archive);

            assertEquals(List.of(), findings);
            assertEquals(archive.toString(), summary.artifact());
            assertEquals(1, summary.classCount());
        } finally {
            System.setProperty(USER_DIR, saved);
        }
    }

    private static String declaringPrefix(String evidence) {
        return evidence.substring(0, evidence.indexOf(DECLARES) + DECLARES.length());
    }

    private static Path handle(Path supplied) {
        return supplied.toAbsolutePath().normalize();
    }

    private Path jar(String name) {
        return EngineFixture.jar(
                scratch, name, EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
    }

    private Path chainingJar(String name, String classPath) {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        return EngineFixture.jar(scratch, name, EngineFixture.withManifest(
                EngineFixture.entries("com/acme/relative/Declaring.class",
                        EngineFixture.classFile("com/acme/relative/Declaring")), manifest));
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
