package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class EmptyRootCheckTest {
    private static final String CODE = "JP3007";
    private static final String APPLICATION_CLASS = "com/acme/app/Order";

    @TempDir
    Path workspace;

    @Test
    void reportsAnApplicationDirectoryWithNoClassFiles() {
        Path root = EngineFixture.classDirectory(workspace, "classes", Map.of());

        Finding finding = EngineFixture.required(
                EngineFixture.verify(List.of(root), List.of(), 17), CODE);

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(root.toString(), finding.artifact().artifact());
        assertEquals(root.toString(), finding.subject());
        assertEquals("application root contributes no classes", finding.summary());
        assertTrue(finding.artifact().classEntry().isEmpty(), finding::toString);
        assertFalse(finding.remediation().isEmpty(), finding::toString);
    }

    @Test
    void reportsAnApplicationArchiveThatCarriesOnlyResources() {
        Path archive = EngineFixture.jar(
                workspace,
                "resources.jar",
                EngineFixture.entries("META-INF/NOTICE", "notice".getBytes(StandardCharsets.UTF_8)));

        assertEquals(
                List.of(CODE), EngineFixture.codes(EngineFixture.verify(List.of(archive), List.of(), 17)));
    }

    @Test
    void staysSilentAboutAnApplicationRootThatDeclaresAClass() {
        Path archive = EngineFixture.jar(
                workspace,
                "app.jar",
                EngineFixture.entries(APPLICATION_CLASS + ".class", EngineFixture.classFile(APPLICATION_CLASS)));

        assertEquals(List.of(), EngineFixture.codes(EngineFixture.verify(List.of(archive), List.of(), 17)));
    }

    /** A classpath entry contributing no classes is ordinary, and the runtime is happy with it. */
    @Test
    void staysSilentAboutAnEmptyClasspathEntry() {
        Path archive = EngineFixture.jar(
                workspace,
                "app.jar",
                EngineFixture.entries(APPLICATION_CLASS + ".class", EngineFixture.classFile(APPLICATION_CLASS)));
        Path empty = EngineFixture.classDirectory(workspace, "lib", Map.of());

        assertEquals(
                List.of(), EngineFixture.codes(EngineFixture.verify(List.of(archive), List.of(empty), 17)));
    }

    @Test
    void reportsOneFindingPerEmptyRoot() {
        Path first = EngineFixture.classDirectory(workspace, "first", Map.of());
        Path second = EngineFixture.classDirectory(workspace, "second", Map.of());

        assertEquals(
                List.of(CODE, CODE),
                EngineFixture.codes(EngineFixture.verify(List.of(first, second), List.of(), 17)));
    }

    /**
     * An archive whose only class file cannot be parsed contributes no classes, and JP3004 already
     * says so. Silence is what this check exists to prevent, and there is none here.
     */
    @Test
    void leavesARootThatReadingAlreadyExplainedToItsOwnFinding() {
        Path archive = EngineFixture.jar(
                workspace, "corrupt.jar", EngineFixture.entries(APPLICATION_CLASS + ".class", new byte[] {1, 2}));

        assertEquals(
                List.of("JP3004"),
                EngineFixture.codes(EngineFixture.verify(List.of(archive), List.of(), 17)));
    }

    @Test
    void showsWhatItObserved() {
        Path root = EngineFixture.classDirectory(workspace, "classes", Map.of());

        Finding finding = EngineFixture.required(
                EngineFixture.verify(List.of(root), List.of(), 17), CODE);

        assertEquals(
                List.of("the root presents no class file to the target runtime"),
                EngineFixture.evidence(finding));
    }
}
