package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.ArtifactSummary;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;

/**
 * Pins that a relative artifact — and a relative {@code --jdk} signature archive — is read through
 * the same base it was validated against.
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
    private static final String SIGNATURE_DIRECTORY = "lib";
    private static final String SIGNATURE_FILE = "ct.sym";
    /** A release with no bundled symbols, so only the supplied archive can answer for it. */
    private static final int UNBUNDLED_RELEASE = 20;

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
                EngineFixture.request(List.of(application), List.of(declaring), 17), new ResourceBudget())
                .entries();

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

    @Test
    void readsARelativeSignatureArchiveAndNeedsNoBundledData() {
        Path home = jdkHome("supplied-jdk");
        Path application = EngineFixture.jar(
                scratch,
                "app11.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE, Opcodes.V11)));

        List<Finding> findings = EngineFixture.verify(jdkRequest(application, home, UNBUNDLED_RELEASE));

        assertEquals(List.of(), findings);
    }

    @Test
    void namesTheCallerTextWhenARelativeJdkHasNoSignatureArchive() throws IOException {
        Path home = Files.createDirectories(scratch.resolve("empty-jdk"));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> EngineFixture.verify(jdkRequest(jar("app.jar"), home, 17)));

        assertCallerText(failure.getMessage(), signatures(home));
    }

    @Test
    void namesTheCallerTextWhenARelativeSignatureArchiveIsNotAnArchive() throws IOException {
        Path home = scratch.resolve("hollow-jdk");
        Path signatures = signatures(home);
        Files.createDirectories(signatures.getParent());
        Files.writeString(signatures, "plain text");

        UncheckedIOException failure = assertThrows(
                UncheckedIOException.class,
                () -> EngineFixture.verify(jdkRequest(jar("app.jar"), home, 17)));

        assertCallerText(failure.getMessage(), signatures);
    }

    /** A report names the relative text the caller wrote, never the absolute path this machine settled. */
    private static void assertCallerText(String message, Path signatures) {
        assertTrue(message.contains(signatures.toString()), () -> message);
        assertFalse(message.contains(handle(signatures).toString()), () -> message);
    }

    /** A JDK-shaped directory, reachable only through a relative path, carrying a real signature archive. */
    private Path jdkHome(String name) {
        Path home = scratch.resolve(name);
        Path signatures = signatures(home);
        try {
            Files.createDirectories(signatures.getParent());
            Files.copy(JdkSymbolResourceGenerator.toolchainCtSym(), signatures);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return home;
    }

    private static Path signatures(Path jdkHome) {
        return jdkHome.resolve(SIGNATURE_DIRECTORY).resolve(SIGNATURE_FILE);
    }

    private static VerificationRequest jdkRequest(Path application, Path jdkHome, int release) {
        return new VerificationRequest(
                List.of(application),
                List.of(),
                TargetRuntime.of(release),
                Scope.APPLICATION,
                Optional.of(jdkHome));
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
