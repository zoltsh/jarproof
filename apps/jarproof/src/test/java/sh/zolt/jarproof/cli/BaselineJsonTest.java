package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;

final class BaselineJsonTest {
    @Test
    void writesTheVersionedEnvelopeInAFixedOrder() {
        BaselineDocument baseline = BaselineDocument.of(
                SampleFindings.request(), "bundled-java-17", SampleFindings.result(SampleFindings.splitPackage()));

        assertEquals(
                """
                {
                  "baselineVersion": "1",
                  "targetJava": 17,
                  "preview": "disabled",
                  "scope": "application",
                  "profile": "bundled-java-17",
                  "fingerprints": [
                    "JP2003|lib/extras.jar||com/acme/orders"
                  ]
                }
                """,
                BaselineJson.write(baseline));
    }

    @Test
    void roundTripsEveryRecordedField() {
        BaselineDocument baseline = new BaselineDocument(
                21, PreviewMode.ENABLED, Scope.ALL, "jdk-21", List.of("JP1001|a.jar||com/acme/Gone"));

        assertEquals(baseline, BaselineJson.read(BaselineJson.write(baseline)));
    }

    @Test
    void writesAnEmptyBaselineAsAnEmptyList() {
        BaselineDocument baseline = new BaselineDocument(17, PreviewMode.DISABLED, Scope.ALL, "jdk", List.of());

        String document = BaselineJson.write(baseline);

        assertTrue(document.contains("\"fingerprints\": []"), document);
        assertEquals(baseline, BaselineJson.read(document));
    }

    @Test
    void refusesAVersionThisBuildDoesNotImplement() {
        String document = BaselineJson.write(
                new BaselineDocument(17, PreviewMode.DISABLED, Scope.ALL, "jdk", List.of()));

        String future = document.replace("\"baselineVersion\": \"1\"", "\"baselineVersion\": \"2\"");

        assertEquals(
                "Unsupported baseline version: 2",
                assertThrows(IllegalArgumentException.class, () -> BaselineJson.read(future)).getMessage());
    }

    @Test
    void refusesAFileThatIsNotABaselineObject() {
        assertEquals(
                "A baseline file must contain a JSON object",
                assertThrows(IllegalArgumentException.class, () -> BaselineJson.read("[]")).getMessage());
    }

    @Test
    void namesTheMemberThatIsMissing() {
        assertEquals(
                "The baseline is missing baselineVersion",
                assertThrows(IllegalArgumentException.class, () -> BaselineJson.read("{}")).getMessage());
        assertEquals(
                "The baseline is missing targetJava",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> BaselineJson.read("{\"baselineVersion\": \"1\"}"))
                        .getMessage());
    }

    @Test
    void namesTheMemberThatHoldsTheWrongKindOfValue() {
        assertEquals(
                "The baseline holds the wrong kind of value for baselineVersion",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> BaselineJson.read("{\"baselineVersion\": 1}"))
                        .getMessage());
        assertEquals(
                "The baseline holds the wrong kind of value for targetJava",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> BaselineJson.read("{\"baselineVersion\": \"1\", \"targetJava\": \"17\"}"))
                        .getMessage());
    }

    @Test
    void namesTheMemberThatHoldsAnUnknownVocabularyValue() {
        String document = "{\"baselineVersion\": \"1\", \"targetJava\": 17, \"preview\": \"maybe\"}";

        assertEquals(
                "The baseline holds an unknown value for preview",
                assertThrows(IllegalArgumentException.class, () -> BaselineJson.read(document)).getMessage());
    }

    @Test
    void refusesFingerprintsThatAreNotAListOfStrings() {
        String notAList = "{\"baselineVersion\": \"1\", \"targetJava\": 17, \"preview\": \"disabled\","
                + " \"scope\": \"all\", \"profile\": \"jdk\", \"fingerprints\": 3}";
        String wrongElements = "{\"baselineVersion\": \"1\", \"targetJava\": 17, \"preview\": \"disabled\","
                + " \"scope\": \"all\", \"profile\": \"jdk\", \"fingerprints\": [4]}";

        assertEquals(
                "The baseline holds the wrong kind of value for fingerprints",
                assertThrows(IllegalArgumentException.class, () -> BaselineJson.read(notAList)).getMessage());
        assertEquals(
                "The baseline holds the wrong kind of value for fingerprints",
                assertThrows(IllegalArgumentException.class, () -> BaselineJson.read(wrongElements)).getMessage());
    }
}
