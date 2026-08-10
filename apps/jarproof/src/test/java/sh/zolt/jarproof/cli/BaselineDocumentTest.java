package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;

final class BaselineDocumentTest {
    private static final PathRoot ROOT = SampleFindings.workingDirectory();

    @Test
    void fingerprintsAFindingByCodeArtifactEntryAndSubject() {
        assertEquals(
                "JP1003|app.jar|com/acme/orders/OrderValidator.class"
                        + "|com/google/common/base/Preconditions#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V",
                BaselineFingerprint.of(ROOT, SampleFindings.missingMethod()));
    }

    @Test
    void leavesTheClassEntryPartEmptyForAnArtifactWideFinding() {
        assertEquals(
                "JP2002|lib/commons-io-2.4.jar||org/apache/commons/io/IOUtils",
                BaselineFingerprint.of(ROOT, SampleFindings.duplicateClass()));
    }

    @Test
    void recordsWhatEveryFindingOfARunWasAcceptedAgainst() {
        BaselineDocument baseline = BaselineDocument.of(
                SampleFindings.request(),
                "bundled-java-17",
                SampleFindings.result(SampleFindings.duplicateClass(), SampleFindings.splitPackage()),
                ROOT);

        assertEquals(17, baseline.targetJava());
        assertEquals(PreviewMode.DISABLED, baseline.preview());
        assertEquals(Scope.APPLICATION, baseline.scope());
        assertEquals("bundled-java-17", baseline.profile());
        assertEquals(2, baseline.fingerprints().size());
    }

    @Test
    void sortsAndDeduplicatesFingerprints() {
        BaselineDocument baseline = new BaselineDocument(
                21, PreviewMode.ENABLED, Scope.ALL, "jdk", List.of("b", "a", "b", "c"));

        assertEquals(List.of("a", "b", "c"), baseline.fingerprints());
    }

    @Test
    void rejectsAMissingRuntimeProfileIdentity() {
        assertThrows(
                NullPointerException.class,
                () -> new BaselineDocument(17, PreviewMode.DISABLED, Scope.ALL, null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BaselineDocument(17, PreviewMode.DISABLED, Scope.ALL, " ", List.of()));
    }

    @Test
    void rejectsMissingVocabularyAndFingerprints() {
        assertThrows(
                NullPointerException.class, () -> new BaselineDocument(17, null, Scope.ALL, "jdk", List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new BaselineDocument(17, PreviewMode.DISABLED, null, "jdk", List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new BaselineDocument(17, PreviewMode.DISABLED, Scope.ALL, "jdk", null));
    }
}
