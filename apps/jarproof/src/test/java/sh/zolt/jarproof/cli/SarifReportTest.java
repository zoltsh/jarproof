package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

final class SarifReportTest {
    @Test
    void rendersAMinimalValidDocument() {
        String document = SarifReport.render(SampleFindings.result(SampleFindings.splitPackage()));

        assertEquals(
                """
                {
                  "version": "2.1.0",
                  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                  "runs": [
                    {
                      "tool": {
                        "driver": {
                          "name": "jarproof",
                          "version": "0.1.0-alpha.1-dev",
                          "rules": [
                            {
                              "id": "JP2003",
                              "shortDescription": {
                                "text": "split package across artifacts"
                              }
                            }
                          ]
                        }
                      },
                      "results": [
                        {
                          "ruleId": "JP2003",
                          "level": "note",
                          "message": {
                            "text": "split package across artifacts"
                          },
                          "locations": [
                            {
                              "physicalLocation": {
                                "artifactLocation": {
                                  "uri": "lib/extras.jar"
                                }
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """,
                document);
    }

    @Test
    void mapsEverySeverityToASarifLevel() {
        assertEquals("note", SarifResult.levelOf(Severity.INFO));
        assertEquals("warning", SarifResult.levelOf(Severity.WARNING));
        assertEquals("error", SarifResult.levelOf(Severity.ERROR));
    }

    @Test
    void declaresOneRulePerDistinctCode() {
        String document = SarifReport.render(SampleFindings.result(
                SampleFindings.missingMethod(), SampleFindings.missingMethod(), SampleFindings.duplicateClass()));

        assertEquals(1, occurrences(document, "\"id\": \"JP1003\""), document);
        assertEquals(1, occurrences(document, "\"id\": \"JP2002\""), document);
        assertEquals(2, occurrences(document, "\"ruleId\": \"JP1003\""), document);
    }

    @Test
    void describesEmptyRunsWithoutRulesOrResults() {
        String document = SarifReport.render(SampleFindings.result());

        assertTrue(document.contains("\"rules\": []"), document);
        assertTrue(document.contains("\"results\": []"), document);
    }

    @Test
    void rendersTheSameBytesOnEveryRun() {
        VerificationResult result = SampleFindings.result(SampleFindings.duplicateClass());

        assertEquals(SarifReport.render(result), SarifReport.render(result));
    }

    private static int occurrences(String document, String fragment) {
        int count = 0;
        int index = document.indexOf(fragment);
        while (index >= 0) {
            count++;
            index = document.indexOf(fragment, index + 1);
        }
        return count;
    }
}
