package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CliFixture.Invocation;

/**
 * The {@code --jdk} override, exercised against the JDK these tests themselves run on.
 *
 * <p>Locating that JDK through {@code java.home} is a test-only move. Production code reads committed
 * resources and paths a caller supplied, and never asks the running JVM where it lives, because a
 * native binary has no JVM to ask and a report must not depend on which JDK happened to launch it.
 */
final class JdkOverrideTest {
    private static final String JDK = "--jdk";
    private static final String OUT = "--out";
    private static final Pattern SUPPLIED_PROFILE = Pattern.compile("\"profile\": \"jdk:17:[0-9a-f]{8}\"");

    @TempDir
    Path workspace;

    @Test
    void readsPlatformSymbolsFromASuppliedJdk() {
        Invocation invocation = check(toolchainHome(), CliFixture.JAVA_17);

        assertEquals(1, invocation.exitCode(), invocation.err());
        assertTrue(invocation.out().contains("JP1003"), invocation.out());
        assertEquals("", invocation.err());
    }

    @Test
    void namesTheSuppliedProfileByTheContentOfItsSignatureArchive() throws IOException {
        Path baseline = workspace.resolve("jarproof-baseline.json");

        Invocation invocation = CliFixture.invoke(
                "baseline",
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                CliFixture.JAVA_17,
                JDK,
                toolchainHome().toString(),
                OUT,
                baseline.toString());

        assertEquals(0, invocation.exitCode(), invocation.err());
        String recorded = Files.readString(baseline);
        assertTrue(SUPPLIED_PROFILE.matcher(recorded).find(), recorded);
    }

    @Test
    void refusesAJdkThatCannotDescribeItsOwnRelease() {
        Invocation invocation = check(toolchainHome(), String.valueOf(Runtime.version().feature()));

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("newer than the target Java release"), invocation.err());
        assertTrue(invocation.err().contains("declares no signatures for Java"), invocation.err());
    }

    @Test
    void refusesASignatureArchiveWithoutTheRootClass() throws IOException {
        Path home = signaturesDeclaring("H/java.base/com/example/Stranger.sig");

        Invocation invocation = check(home, CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains("declares no java/lang/Object for Java 17"), invocation.err());
        assertTrue(invocation.err().contains("newer than the target Java release"), invocation.err());
    }

    @Test
    void refusesAJdkWithNoSignatureArchiveAtAll() throws IOException {
        Path home = Files.createDirectories(workspace.resolve("empty-jdk"));

        Invocation invocation = check(home, CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertTrue(
                invocation.err().contains(home.resolve("lib").resolve("ct.sym").toString()),
                invocation.err());
    }

    @Test
    void refusesASignatureArchiveThatIsNotAnArchive() throws IOException {
        Path home = Files.createDirectories(workspace.resolve("hollow-jdk"));
        Path signatures = home.resolve("lib").resolve("ct.sym");
        Files.createDirectories(signatures.getParent());
        Files.writeString(signatures, "plain text", StandardCharsets.UTF_8);

        Invocation invocation = check(home, CliFixture.JAVA_17);

        assertEquals(2, invocation.exitCode());
        assertTrue(invocation.err().contains(signatures.toString()), invocation.err());
    }

    private Invocation check(Path home, String release) {
        return CliFixture.invoke(
                CliFixture.CHECK,
                CliFixture.APPLICATION,
                CliFixture.brokenApplication(workspace).toString(),
                CliFixture.CLASSPATH,
                CliFixture.library(workspace).toString(),
                CliFixture.TARGET_JAVA,
                release,
                JDK,
                home.toString());
    }

    private static Path toolchainHome() {
        return Path.of(System.getProperty("java.home"));
    }

    /** A JDK-shaped directory whose signature archive declares exactly one class. */
    private Path signaturesDeclaring(String entryName) throws IOException {
        Path home = workspace.resolve("partial-jdk");
        Path signatures = home.resolve("lib").resolve("ct.sym");
        Files.createDirectories(signatures.getParent());
        try (OutputStream file = Files.newOutputStream(signatures);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(CliFixture.classFile("com/example/Stranger", 61));
            zip.closeEntry();
        }
        return home;
    }
}
