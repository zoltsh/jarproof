package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

final class PathRootTest {
    private static final Path ROOT = Path.of("/projects/orders");
    private static final String APPLICATION = "/projects/orders/app.jar";

    @Test
    void measuresAnArtifactFromTheRoot() {
        assertEquals("lib/guava-18.0.jar", rewritten("/projects/orders/lib/guava-18.0.jar"));
    }

    @Test
    void measuresAnArtifactAboveTheRootAsAWayUp() {
        assertEquals("../vendor/legacy.jar", rewritten("/projects/vendor/legacy.jar"));
    }

    @Test
    void keepsTheArtifactPathWhenItIsTheRootItself() {
        assertEquals("/projects/orders", rewritten("/projects/orders"));
    }

    @Test
    void keepsTextThatIsNotAPathOnThisPlatform() {
        String impossible = "lib/brok\0en.jar";

        assertEquals(impossible, rewritten(impossible));
    }

    @Test
    void leavesEverythingExceptTheArtifactPathAlone() {
        Finding rewritten = PathRoot.of(ROOT).rewrite(result(APPLICATION)).findings().getFirst();
        Finding original = sample(APPLICATION);

        assertEquals(original.code(), rewritten.code());
        assertEquals(original.severity(), rewritten.severity());
        assertEquals(original.predictedError(), rewritten.predictedError());
        assertEquals(original.artifact().classEntry(), rewritten.artifact().classEntry());
        assertEquals(original.subject(), rewritten.subject());
        assertEquals(original.summary(), rewritten.summary());
        assertEquals(original.explanation(), rewritten.explanation());
        assertEquals(original.evidence(), rewritten.evidence());
        assertEquals(original.remediation(), rewritten.remediation());
    }

    @Test
    void rewritesEveryFindingItIsGiven() {
        VerificationResult measured = PathRoot.of(ROOT).rewrite(new VerificationResult(
                List.of(sample(APPLICATION), sample("/projects/orders/lib/extra.jar"))));

        assertEquals(
                List.of("app.jar", "lib/extra.jar"),
                measured.findings().stream().map(finding -> finding.artifact().artifact()).toList());
    }

    private static String rewritten(String artifact) {
        return PathRoot.of(ROOT).rewrite(result(artifact)).findings().getFirst().artifact().artifact();
    }

    private static VerificationResult result(String artifact) {
        return new VerificationResult(List.of(sample(artifact)));
    }

    private static Finding sample(String artifact) {
        return new Finding(
                FindingCode.of("JP1001"),
                Severity.ERROR,
                PredictedError.NO_CLASS_DEF_FOUND_ERROR,
                ArtifactLocation.ofClassEntry(artifact, "com/acme/App.class"),
                "com/acme/Absent",
                "referenced class is absent",
                "Nothing declares it.",
                List.of(new Evidence("nothing on the classpath declares com/acme/Absent")),
                List.of());
    }
}
