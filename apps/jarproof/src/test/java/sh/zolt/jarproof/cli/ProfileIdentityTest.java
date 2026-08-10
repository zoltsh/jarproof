package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProfileIdentityTest {
    private static final Pattern SUPPLIED = Pattern.compile("jdk:17:[0-9a-f]{8}");

    @TempDir
    Path workspace;

    @Test
    void namesTheBundledProfileByItsRelease() {
        assertEquals("bundled:17", ProfileIdentity.of(17, Optional.empty()));
        assertEquals("bundled:21", ProfileIdentity.of(21, Optional.empty()));
    }

    @Test
    void namesASuppliedProfileByReleaseAndArchiveContent() throws IOException {
        Path home = jdkHome("home", "signatures");

        String identity = ProfileIdentity.of(17, Optional.of(home));

        assertTrue(SUPPLIED.matcher(identity).matches(), identity);
    }

    @Test
    void namesTheSameArchiveIdenticallyWhereverItIsInstalled() throws IOException {
        Path here = jdkHome("here", "signatures");
        Path there = jdkHome("there", "signatures");

        assertEquals(ProfileIdentity.of(17, Optional.of(here)), ProfileIdentity.of(17, Optional.of(there)));
    }

    @Test
    void namesDifferentArchivesDifferently() throws IOException {
        Path first = jdkHome("first", "signatures");
        Path second = jdkHome("second", "other signatures");

        assertNotEquals(ProfileIdentity.of(17, Optional.of(first)), ProfileIdentity.of(17, Optional.of(second)));
    }

    @Test
    void separatesTheSameArchiveByRelease() throws IOException {
        Path home = jdkHome("shared", "signatures");

        assertNotEquals(ProfileIdentity.of(17, Optional.of(home)), ProfileIdentity.of(11, Optional.of(home)));
    }

    @Test
    void refusesAJdkWhoseSignatureArchiveCannotBeRead() {
        Path home = workspace.resolve("absent-jdk");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> ProfileIdentity.of(17, Optional.of(home)));

        assertTrue(
                failure.getMessage().contains(ProfileIdentity.signatureArchive(home).toString()),
                failure::getMessage);
    }

    private Path jdkHome(String name, String content) throws IOException {
        Path home = workspace.resolve(name);
        Path archive = ProfileIdentity.signatureArchive(home);
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, content, StandardCharsets.UTF_8);
        return home;
    }
}
