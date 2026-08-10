package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class CorruptClassEntryTest {
    private static final String BROKEN_ENTRY = "com/acme/broken/Broken.class";

    @TempDir
    Path workspace;

    @Test
    void reportsAnEntryThatIsNoClassFileAtAll() {
        Path archive = archive("this is not bytecode".getBytes(StandardCharsets.UTF_8));

        Finding finding = EngineFixture.required(EngineFixture.verify(List.of(archive), List.of(), 17), "JP3004");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(BROKEN_ENTRY, finding.subject());
        assertEquals(Optional.of(BROKEN_ENTRY), finding.artifact().classEntry());
        assertEquals(List.of(), indexed(archive));
    }

    @Test
    void reportsAnEntryShorterThanAClassFileHeader() {
        Path archive = archive(new byte[] {(byte) 0xCA, (byte) 0xFE});

        assertEquals(List.of("JP3004"), EngineFixture.codes(EngineFixture.verify(List.of(archive), List.of(), 17)));
    }

    @Test
    void stillIndexesAnEntryWhoseHeaderSurvivedTheDamage() {
        Path archive = archive(Arrays.copyOf(EngineFixture.classFile("com/acme/broken/Broken"), 8));

        List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);

        assertTrue(EngineFixture.coded(findings, "JP3004").isPresent(), EngineFixture.codes(findings).toString());
        assertEquals(List.of("com/acme/broken/Broken"), indexed(archive));
    }

    @Test
    void survivesAnEntryWithNothingButAClassFileSignature() {
        Path archive = archive(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 61});

        assertEquals(List.of("JP3004"), EngineFixture.codes(EngineFixture.verify(List.of(archive), List.of(), 17)));
    }

    private List<String> indexed(Path archive) {
        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(List.of(archive), List.of(), 17), new ResourceBudget());
        return catalog.artifacts().get(0).classes().stream().map(IndexedClass::internalName).toList();
    }

    private Path archive(byte[] content) {
        return EngineFixture.jar(workspace, "lib/broken.jar", EngineFixture.entries(BROKEN_ENTRY, content));
    }
}
