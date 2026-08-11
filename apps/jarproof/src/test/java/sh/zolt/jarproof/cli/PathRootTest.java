package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.ArtifactSummary;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

final class PathRootTest {
    private static final String NESTED_CLASSES = "!/BOOT-INF/classes";
    private static final String LIBRARY = "lib/api.jar";

    @TempDir
    Path workspace;

    @Test
    void measuresAnArtifactFromTheRoot() {
        assertEquals(LIBRARY, rewritten(workspace.resolve(LIBRARY).toString()));
    }

    @Test
    void normalisesAPathThatNamesTheRootTheLongWayRound() {
        assertEquals(LIBRARY, rewritten(workspace + "/./" + LIBRARY));
    }

    /**
     * A {@code ../..} chain measures how deep the checkout directory sits, which is exactly the
     * machine-specific text this root exists to remove, so an artifact above the root keeps its own
     * normalised spelling instead.
     */
    @Test
    void keepsTheCallersOwnTextForAnArtifactAboveTheRoot() {
        Path above = workspace.getParent().resolve("legacy.jar");

        assertEquals(above.toString(), rewritten(above.toString()));
        assertEquals(above.toString(), rewritten(workspace + "/../legacy.jar"));
    }

    /**
     * Measuring against the file system root would strip the leading separator off every absolute
     * path, which reads as a relative path and resolves only from {@code /}.
     */
    @Test
    void keepsAnAbsolutePathWhenTheRootIsTheFileSystemRoot() {
        String artifact = workspace.resolve(LIBRARY).toString();

        assertEquals(
                artifact,
                PathRoot.of(workspace.getRoot())
                        .rewrite(request(), result(artifact))
                        .findings()
                        .get(0)
                        .artifact()
                        .artifact());
    }

    @Test
    void keepsTheArtifactPathWhenItIsTheRootItself() {
        assertEquals(workspace.toString(), rewritten(workspace.toString()));
    }

    /** Text that normalises away entirely is text, not a measurement, so it is repeated. */
    @Test
    void keepsAPathThatNormalisesToNothing() {
        assertEquals(".", rewritten("."));
    }

    @Test
    void keepsTextThatIsNotAPathOnThisPlatform() {
        String impossible = "lib/brok\0en.jar";

        assertEquals(impossible, rewritten(impossible));
    }

    /** The outer archive is the caller's text; the entry inside it is not a path at all. */
    @Test
    void measuresOnlyTheArchiveOfAPositionInsideOne() {
        assertEquals(
                "app.jar" + NESTED_CLASSES,
                rewritten(workspace.resolve("app.jar") + NESTED_CLASSES));
    }

    @Test
    void refusesARootThatIsNotADirectoryThatExists() {
        Path absent = workspace.resolve("nowhere");

        assertEquals(
                "This path root is not a directory that exists: " + absent,
                assertThrows(IllegalArgumentException.class, () -> PathRoot.of(absent)).getMessage());
    }

    @Test
    void refusesARootThatIsAFile() throws IOException {
        Path file = Files.writeString(workspace.resolve("root.txt"), "x\n");

        assertThrows(IllegalArgumentException.class, () -> PathRoot.of(file));
    }

    @Test
    void leavesEverythingExceptThePathsAlone() {
        String artifact = workspace.resolve("app.jar").toString();
        Finding rewritten = PathRoot.of(workspace).rewrite(request(), result(artifact)).findings().get(0);
        Finding original = sample(artifact);

        assertEquals(original.code(), rewritten.code());
        assertEquals(original.severity(), rewritten.severity());
        assertEquals(original.predictedError(), rewritten.predictedError());
        assertEquals(original.artifact().classEntry(), rewritten.artifact().classEntry());
        assertEquals(original.artifact().sourceFile(), rewritten.artifact().sourceFile());
        assertEquals(original.artifact().line(), rewritten.artifact().line());
        assertEquals(original.subject(), rewritten.subject());
        assertEquals(original.summary(), rewritten.summary());
        assertEquals(original.explanation(), rewritten.explanation());
        assertEquals(original.evidence(), rewritten.evidence());
        assertEquals(original.remediation(), rewritten.remediation());
    }

    @Test
    void rewritesEveryFindingItIsGiven() {
        VerificationResult measured = PathRoot.of(workspace).rewrite(request(), VerificationResult.of(
                List.of(sample(workspace.resolve("app.jar").toString()), sample(workspace.resolve(LIBRARY).toString()))));

        assertEquals(
                List.of("app.jar", LIBRARY),
                measured.findings().stream().map(finding -> finding.artifact().artifact()).toList());
    }

    /** Measuring paths says nothing about how much was read, so the run's own tallies travel with it. */
    @Test
    void keepsWhatTheRunExaminedWhileItMeasuresThePaths() {
        VerificationResult run = new VerificationResult(
                List.of(sample(workspace.resolve("app.jar").toString())), 5000, 40);

        VerificationResult measured = PathRoot.of(workspace).rewrite(request(), run);

        assertEquals(5000, measured.analyzedClassCount());
        assertEquals(40, measured.analyzedArtifactCount());
        assertEquals("app.jar", measured.findings().get(0).artifact().artifact());
    }

    @Test
    void measuresTheArtifactOfAnInspection() {
        ArtifactSummary read = new ArtifactSummary(
                workspace.resolve(LIBRARY).toString(), 2, 1, 0, List.of("61:1"), List.of(), List.of());

        ArtifactSummary measured = PathRoot.of(workspace).rewrite(read);

        assertEquals(LIBRARY, measured.artifact());
        assertEquals(read.entryCount(), measured.entryCount());
        assertEquals(read.classCount(), measured.classCount());
        assertEquals(read.bytecodeLevels(), measured.bytecodeLevels());
    }

    private String rewritten(String artifact) {
        return PathRoot.of(workspace)
                .rewrite(request(), result(artifact))
                .findings()
                .get(0)
                .artifact()
                .artifact();
    }

    private VerificationRequest request() {
        return VerificationRequest.of(
                List.of(workspace.resolve("app.jar")),
                List.of(workspace.resolve(LIBRARY)),
                TargetRuntime.of(17),
                Scope.APPLICATION);
    }

    private static VerificationResult result(String artifact) {
        return VerificationResult.of(List.of(sample(artifact)));
    }

    private static Finding sample(String artifact) {
        return new Finding(
                FindingCode.of("JP1001"),
                Severity.ERROR,
                PredictedError.NO_CLASS_DEF_FOUND_ERROR,
                ArtifactLocation.ofSource(
                        artifact, "com/acme/App.class", Optional.of("App.java"), Optional.of(17)),
                "com/acme/Absent",
                "referenced class is absent",
                "Nothing declares it.",
                List.of(new Evidence("nothing on the classpath declares com/acme/Absent")),
                List.of());
    }
}
