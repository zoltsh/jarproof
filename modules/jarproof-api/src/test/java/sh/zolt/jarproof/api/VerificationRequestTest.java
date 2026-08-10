package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class VerificationRequestTest {
    private static final Path CORE = Path.of("app-core.jar");
    private static final Path WEB = Path.of("app-web.jar");
    private static final List<Path> APPLICATIONS = List.of(CORE, WEB);
    private static final Path FIRST = Path.of("lib/guava-18.0.jar");
    private static final Path SECOND = Path.of("lib/jackson-databind-2.17.0.jar");
    private static final List<Path> CLASSPATH = List.of(FIRST, SECOND);
    private static final TargetRuntime RUNTIME = TargetRuntime.of(17);
    private static final Optional<Path> JDK_HOME = Optional.of(Path.of("/opt/jdk-21"));

    @Test
    void keepsEverySuppliedComponentInOrder() {
        VerificationRequest request = sample();

        assertEquals(List.of(CORE, WEB), request.applications());
        assertEquals(List.of(FIRST, SECOND), request.classpath());
        assertEquals(RUNTIME, request.targetRuntime());
        assertEquals(Scope.APPLICATION, request.scope());
        assertEquals(Optional.empty(), request.jdkHome());
    }

    @Test
    void keepsTheJdkInstallationItWasGiven() {
        VerificationRequest request =
                new VerificationRequest(APPLICATIONS, CLASSPATH, RUNTIME, Scope.APPLICATION, JDK_HOME);

        assertEquals(JDK_HOME, request.jdkHome());
    }

    @Test
    void copiesBothListsDefensively() {
        List<Path> applications = new ArrayList<>(APPLICATIONS);
        List<Path> classpath = new ArrayList<>(CLASSPATH);
        VerificationRequest request = VerificationRequest.of(applications, classpath, RUNTIME, Scope.ALL);

        applications.add(Path.of("app-added-after-construction.jar"));
        classpath.add(Path.of("lib/added-after-construction.jar"));

        assertEquals(APPLICATIONS, request.applications());
        assertEquals(CLASSPATH, request.classpath());
    }

    @Test
    void publishesUnmodifiableLists() {
        VerificationRequest request = sample();
        Path extra = Path.of("lib/late.jar");

        assertThrows(UnsupportedOperationException.class, () -> request.applications().add(extra));
        assertThrows(UnsupportedOperationException.class, () -> request.classpath().add(extra));
    }

    @Test
    void acceptsAnEmptyClasspath() {
        VerificationRequest request = VerificationRequest.of(APPLICATIONS, List.of(), RUNTIME, Scope.ALL);

        assertEquals(List.of(), request.classpath());
    }

    @Test
    void rejectsAnEmptyApplicationList() {
        assertThrows(IllegalArgumentException.class,
                () -> VerificationRequest.of(List.of(), CLASSPATH, RUNTIME, Scope.APPLICATION));
    }

    @Test
    void rejectsAnyMissingComponent() {
        assertThrows(NullPointerException.class,
                () -> VerificationRequest.of(null, CLASSPATH, RUNTIME, Scope.APPLICATION));
        assertThrows(NullPointerException.class,
                () -> VerificationRequest.of(APPLICATIONS, null, RUNTIME, Scope.APPLICATION));
        assertThrows(NullPointerException.class,
                () -> VerificationRequest.of(APPLICATIONS, CLASSPATH, null, Scope.APPLICATION));
        assertThrows(NullPointerException.class,
                () -> VerificationRequest.of(APPLICATIONS, CLASSPATH, RUNTIME, null));
        assertThrows(NullPointerException.class,
                () -> new VerificationRequest(APPLICATIONS, CLASSPATH, RUNTIME, Scope.APPLICATION, null));
    }

    @Test
    void comparesByEveryComponent() {
        VerificationRequest request = sample();

        assertEquals(request, sample());
        assertEquals(request.hashCode(), sample().hashCode());
        assertNotEquals(request, VerificationRequest.of(APPLICATIONS, CLASSPATH, RUNTIME, Scope.ALL));
        assertNotEquals(request, VerificationRequest.of(List.of(CORE), CLASSPATH, RUNTIME, Scope.APPLICATION));
        assertNotEquals(
                request,
                VerificationRequest.of(APPLICATIONS, List.of(SECOND, FIRST), RUNTIME, Scope.APPLICATION));
        assertNotEquals(
                request,
                new VerificationRequest(APPLICATIONS, CLASSPATH, RUNTIME, Scope.APPLICATION, JDK_HOME));
    }

    private static VerificationRequest sample() {
        return VerificationRequest.of(APPLICATIONS, CLASSPATH, RUNTIME, Scope.APPLICATION);
    }
}
