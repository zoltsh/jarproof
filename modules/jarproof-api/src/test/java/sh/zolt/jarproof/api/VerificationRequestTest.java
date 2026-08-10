package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VerificationRequestTest {
    private static final Path CORE = Path.of("app-core.jar");
    private static final Path WEB = Path.of("app-web.jar");
    private static final List<Path> APPLICATIONS = List.of(CORE, WEB);
    private static final Path FIRST = Path.of("lib/guava-18.0.jar");
    private static final Path SECOND = Path.of("lib/jackson-databind-2.17.0.jar");
    private static final List<Path> CLASSPATH = List.of(FIRST, SECOND);
    private static final TargetRuntime RUNTIME = TargetRuntime.of(17);

    @Test
    void keepsEverySuppliedComponentInOrder() {
        VerificationRequest request = sample();

        assertEquals(List.of(CORE, WEB), request.applications());
        assertEquals(List.of(FIRST, SECOND), request.classpath());
        assertEquals(RUNTIME, request.targetRuntime());
        assertEquals(Scope.APPLICATION, request.scope());
    }

    @Test
    void copiesBothListsDefensively() {
        List<Path> applications = new ArrayList<>(APPLICATIONS);
        List<Path> classpath = new ArrayList<>(CLASSPATH);
        VerificationRequest request = new VerificationRequest(applications, classpath, RUNTIME, Scope.ALL);

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
        VerificationRequest request = new VerificationRequest(APPLICATIONS, List.of(), RUNTIME, Scope.ALL);

        assertEquals(List.of(), request.classpath());
    }

    @Test
    void rejectsAnEmptyApplicationList() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationRequest(List.of(), CLASSPATH, RUNTIME, Scope.APPLICATION));
    }

    @Test
    void rejectsAnyMissingComponent() {
        assertThrows(NullPointerException.class,
                () -> new VerificationRequest(null, CLASSPATH, RUNTIME, Scope.APPLICATION));
        assertThrows(NullPointerException.class,
                () -> new VerificationRequest(APPLICATIONS, null, RUNTIME, Scope.APPLICATION));
        assertThrows(NullPointerException.class,
                () -> new VerificationRequest(APPLICATIONS, CLASSPATH, null, Scope.APPLICATION));
        assertThrows(NullPointerException.class,
                () -> new VerificationRequest(APPLICATIONS, CLASSPATH, RUNTIME, null));
    }

    @Test
    void comparesByEveryComponent() {
        VerificationRequest request = sample();

        assertEquals(request, sample());
        assertEquals(request.hashCode(), sample().hashCode());
        assertNotEquals(request, new VerificationRequest(APPLICATIONS, CLASSPATH, RUNTIME, Scope.ALL));
        assertNotEquals(request, new VerificationRequest(List.of(CORE), CLASSPATH, RUNTIME, Scope.APPLICATION));
        assertNotEquals(
                request,
                new VerificationRequest(APPLICATIONS, List.of(SECOND, FIRST), RUNTIME, Scope.APPLICATION));
    }

    private static VerificationRequest sample() {
        return new VerificationRequest(APPLICATIONS, CLASSPATH, RUNTIME, Scope.APPLICATION);
    }
}
