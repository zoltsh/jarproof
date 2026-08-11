package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class ClassFileVersionCheckTest {
    private static final String FUTURE = "com/acme/future/Future";
    private static final String FUTURE_ENTRY = "com/acme/future/Future.class";
    private static final String ABSENT = "com/acme/future/Absent";
    private static final int UNKNOWN_MAJOR = 99;

    /** The highest class file version the bundled bytecode parser accepts. */
    private static final int NEWEST_PARSED_MAJOR = Opcodes.V26;

    @TempDir
    Path workspace;

    @Test
    void reportsAClassFileNewerThanTheTarget() {
        Path archive = archive(EngineFixture.withVersion(base(), EngineFixture.JAVA_21_MAJOR, 0));

        Finding finding = EngineFixture.required(EngineFixture.verify(List.of(archive), List.of(), 17), "JP3001");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.UNSUPPORTED_CLASS_VERSION_ERROR, finding.predictedError());
        assertEquals(FUTURE, finding.subject());
        assertEquals(Optional.of(FUTURE_ENTRY), finding.artifact().classEntry());
        assertTrue(EngineFixture.evidence(finding).contains("the class file targets Java 21"),
                EngineFixture.evidence(finding).toString());
        assertTrue(EngineFixture.evidence(finding).contains("the target runtime accepts Java 17 at most"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void staysQuietWhenTheClassFileMatchesTheTarget() {
        Path archive = archive(base());

        assertEquals(List.of(), EngineFixture.verify(List.of(archive), List.of(), 17));
    }

    @Test
    void reportsAPreviewClassFileWhenPreviewIsDisabled() {
        Path archive = archive(
                EngineFixture.withVersion(base(), EngineFixture.JAVA_17_MAJOR, EngineFixture.PREVIEW_MINOR));

        List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);
        Finding finding = EngineFixture.required(findings, "JP3002");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.UNSUPPORTED_CLASS_VERSION_ERROR, finding.predictedError());
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP3001"));
        assertTrue(EngineFixture.evidence(finding).contains("it is usable only on Java 17 with preview"
                + " features enabled"), EngineFixture.evidence(finding).toString());
    }

    @Test
    void acceptsAPreviewClassFileForTheExactTargetWhenPreviewIsEnabled() {
        Path archive = archive(
                EngineFixture.withVersion(base(), EngineFixture.JAVA_17_MAJOR, EngineFixture.PREVIEW_MINOR));

        assertEquals(List.of(), EngineFixture.verify(EngineFixture.previewRequest(List.of(archive), 17)));
    }

    @Test
    void reportsAPreviewClassFileFromAnotherReleaseEvenWhenPreviewIsEnabled() {
        Path archive = archive(
                EngineFixture.withVersion(base(), EngineFixture.JAVA_21_MAJOR, EngineFixture.PREVIEW_MINOR));

        List<Finding> findings = EngineFixture.verify(EngineFixture.previewRequest(List.of(archive), 17));

        assertEquals(List.of("JP3001", "JP3002"), EngineFixture.codes(findings));
    }

    @Test
    void treatsAClassFileNoParserUnderstandsAsTooNewRatherThanCorrupt() {
        Path archive = archive(EngineFixture.withVersion(base(), UNKNOWN_MAJOR, 0));

        List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);

        assertEquals(List.of("JP3001"), EngineFixture.codes(findings));
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP3004"));
    }

    /**
     * The newest class file version any parser here understands is still parsed, so the references written
     * in it are still resolved. One version higher is recorded with its number and no shape, which is what
     * the version finding above reports; treating this one that way would drop the class out of linkage
     * analysis altogether and report the artifact as sound.
     */
    @Test
    void resolvesTheReferencesOfTheNewestClassFileVersionItCanParse() {
        Path archive = archive(EngineFixture.withVersion(broken(), NEWEST_PARSED_MAJOR, 0));

        List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);

        assertEquals(List.of("JP1001", "JP3001"), EngineFixture.codes(findings));
        assertEquals(ABSENT, EngineFixture.required(findings, "JP1001").subject());
    }

    private Path archive(byte[] classFile) {
        return EngineFixture.jar(workspace, "lib/versioned.jar", EngineFixture.entries(FUTURE_ENTRY, classFile));
    }

    private static byte[] base() {
        return EngineFixture.classFile(FUTURE);
    }

    /** The same class with a body: one static call to a type nothing on the classpath declares. */
    private static byte[] broken() {
        return LinkageFixture.caller(
                FUTURE, List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ABSENT, "reset", "()V")));
    }
}
