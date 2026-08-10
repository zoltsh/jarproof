package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

final class JarproofVerifyTest {
    @TempDir
    Path workspace;

    @Test
    void rejectsAMissingRequest() {
        assertThrows(NullPointerException.class, () -> Jarproof.verify(null));
    }

    @Test
    void rejectsAnApplicationThatIsNotThere() {
        VerificationRequest request =
                EngineFixture.request(List.of(workspace.resolve("absent.jar")), List.of(), 17);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> Jarproof.verify(request));

        assertTrue(failure.getMessage().contains("application artifact"), failure.getMessage());
    }

    @Test
    void rejectsAClasspathEntryThatIsNotThere() {
        VerificationRequest request = EngineFixture.request(
                List.of(application()), List.of(workspace.resolve("absent.jar")), 17);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> Jarproof.verify(request));

        assertTrue(failure.getMessage().contains("classpath entry"), failure.getMessage());
    }

    @Test
    void rejectsAWildcardWithoutADirectory() {
        VerificationRequest request = EngineFixture.request(
                List.of(application()), List.of(workspace.resolve("absent").resolve("*")), 17);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> Jarproof.verify(request));

        assertTrue(failure.getMessage().contains("wildcard"), failure.getMessage());
    }

    @Test
    void rejectsAClasspathEntryThatIsNotAnArchive() throws IOException {
        Path notAnArchive = Files.writeString(workspace.resolve("notes.txt"), "plain text");
        VerificationRequest request = EngineFixture.request(List.of(application()), List.of(notAnArchive), 17);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> Jarproof.verify(request));

        assertTrue(failure.getMessage().contains("JAR archive"), failure.getMessage());
    }

    @Test
    void reportsNothingForAHealthyClasspath() {
        VerificationResult result = Jarproof.verify(EngineFixture.request(List.of(application()), List.of(), 17));

        assertEquals(List.of(), result.findings());
        assertFalse(result.hasErrors());
    }

    @Test
    void readsAClassDirectoryTheWayTheLauncherWould() {
        Path classes = EngineFixture.classDirectory(
                workspace, "classes", EngineFixture.entries("com/acme/Loose.class",
                        EngineFixture.classFile("com/acme/Loose")));

        VerificationResult result = Jarproof.verify(EngineFixture.request(List.of(classes), List.of(), 17));

        assertEquals(List.of(), result.findings());
    }

    @Test
    void acceptsAnApplicationBesideItsOwnClasspath() {
        Path library = EngineFixture.jar(workspace, "lib/library.jar",
                EngineFixture.entries("com/acme/lib/Library.class",
                        EngineFixture.classFile("com/acme/lib/Library")));

        VerificationResult result =
                Jarproof.verify(EngineFixture.request(List.of(application()), List.of(library), 17));

        assertEquals(List.of(), result.findings());
    }

    private Path application() {
        return EngineFixture.jar(workspace, "app.jar",
                EngineFixture.entries("com/acme/App.class", EngineFixture.classFile("com/acme/App")));
    }
}
