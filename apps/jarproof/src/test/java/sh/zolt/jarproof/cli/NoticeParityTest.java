package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The uber JAR redistributes third-party software, so the legal notices must travel inside it —
 * a notice that exists only in the repository covers nothing a user downloads as a single jar.
 * The embedded copies are duplicates of the repository files by necessity, and this test is the
 * contract that keeps the duplicates honest: edit one without the other and the build refuses.
 */
final class NoticeParityTest {
    @Test
    void embeddedNoticeMatchesTheRepositoryFile() throws IOException {
        assertResourceMatches("/META-INF/NOTICE", "NOTICE");
    }

    @Test
    void embeddedThirdPartyNoticesMatchTheRepositoryFile() throws IOException {
        assertResourceMatches("/META-INF/THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md");
    }

    private static void assertResourceMatches(String resource, String fileName) throws IOException {
        byte[] embedded;
        try (InputStream stream = NoticeParityTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " is not on the classpath");
            embedded = stream.readAllBytes();
        }
        assertArrayEquals(Files.readAllBytes(workspaceRoot().resolve(fileName)), embedded,
                fileName + " and its embedded copy have drifted apart; copy the repository file over"
                        + " apps/jarproof/src/main/resources" + resource);
    }

    private static Path workspaceRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("NOTICE"))) {
            candidate = candidate.getParent();
        }
        assertNotNull(candidate, "No NOTICE file above the working directory");
        return candidate;
    }
}
