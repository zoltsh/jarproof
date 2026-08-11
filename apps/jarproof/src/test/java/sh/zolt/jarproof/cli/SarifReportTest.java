package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

final class SarifReportTest {
    private static final String SOURCE_ROOT = "src/main/java";
    private static final String GENERATED_ROOT = "generated";
    private static final String URI_MEMBER = "\"uri\": \"";

    @TempDir
    Path workspace;

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
                          "version": "0.0.1-SNAPSHOT",
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
                            "text": "split package across artifacts",
                            "markdown": "**split package across artifacts**\\u000A\\u000AThe package is assembled from more than one artifact, which a classpath allows."
                          },
                          "locations": [
                            {
                              "physicalLocation": {
                                "artifactLocation": {
                                  "uri": "lib/extras.jar"
                                }
                              }
                            }
                          ],
                          "properties": {
                            "evidence": [],
                            "remediation": []
                          }
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

    @Test
    void locatesAResultInTheSourceFileARootHolds() throws IOException {
        String document = SarifReport.render(atLine(SampleFindings.SOURCE_LINE), List.of(sourceRoot()));

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
                          "version": "0.0.1-SNAPSHOT",
                          "rules": [
                            {
                              "id": "JP1003",
                              "shortDescription": {
                                "text": "missing method"
                              }
                            }
                          ]
                        }
                      },
                      "results": [
                        {
                          "ruleId": "JP1003",
                          "level": "error",
                          "message": {
                            "text": "missing method",
                            "markdown": "**missing method**\\u000A\\u000Aguava-18.0.jar declares no checkArgument(boolean, String, Object) on Preconditions.\\u000A\\u000A- selected guava-18.0.jar from lib/\\\\*\\u000A- app.jar was compiled against a newer guava\\u000A\\u000A**next:** align the runtime classpath with the version used to compile app.jar"
                          },
                          "locations": [
                            {
                              "physicalLocation": {
                                "artifactLocation": {
                                  "uri": "com/acme/orders/OrderValidator.java"
                                },
                                "region": {
                                  "startLine": 42
                                }
                              }
                            }
                          ],
                          "properties": {
                            "evidence": [
                              "selected guava-18.0.jar from lib/*",
                              "app.jar was compiled against a newer guava"
                            ],
                            "remediation": [
                              "align the runtime classpath with the version used to compile app.jar",
                              "or recompile app.jar against guava-18.0.jar"
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """,
                document);
    }

    @Test
    void takesTheFirstRootThatHoldsTheSourceFile() throws IOException {
        Path generated = emptyRoot();
        Path source = sourceRoot();

        String document = SarifReport.render(atLine(SampleFindings.SOURCE_LINE), List.of(generated, source));

        assertFalse(Files.exists(generated.resolve(SampleFindings.SOURCE_PATH)), generated.toString());
        assertTrue(document.contains(URI_MEMBER + SampleFindings.SOURCE_PATH + "\""), document);
    }

    @Test
    void omitsTheRegionWhenTheClassFileRecordedNoLine() throws IOException {
        String document = SarifReport.render(inSource(Optional.empty()), List.of(sourceRoot()));

        assertTrue(document.contains(URI_MEMBER + SampleFindings.SOURCE_PATH + "\""), document);
        assertFalse(document.contains("region"), document);
    }

    @Test
    void keepsTheArtifactLevelResultWhenNoRootHoldsTheSourceFile() throws IOException {
        VerificationResult result = atLine(SampleFindings.SOURCE_LINE);
        String artifactLevel = SarifReport.render(result);

        assertEquals(artifactLevel, SarifReport.render(result, List.of(emptyRoot())));
        assertEquals(artifactLevel, SarifReport.render(result, List.of()));
        assertTrue(artifactLevel.contains(URI_MEMBER + SampleFindings.APPLICATION + "\""), artifactLevel);
        assertFalse(artifactLevel.contains("region"), artifactLevel);
    }

    @Test
    void keepsTheArtifactLevelResultForAFindingThatNamesNoSourceFile() throws IOException {
        VerificationResult result = SampleFindings.result(SampleFindings.duplicateClass());

        assertEquals(SarifReport.render(result), SarifReport.render(result, List.of(sourceRoot())));
    }

    @Test
    void keepsTheArtifactLevelResultForASourceNameThatIsNotAPathHere() throws IOException {
        Finding awkward = SampleFindings.missingMethodInSource(Optional.empty());
        VerificationResult result = SampleFindings.result(new Finding(
                awkward.code(),
                awkward.severity(),
                awkward.predictedError(),
                ArtifactLocation.ofSource(
                        SampleFindings.APPLICATION,
                        SampleFindings.CLASS_ENTRY,
                        Optional.of("od\0d.java"),
                        Optional.empty()),
                awkward.subject(),
                awkward.summary(),
                awkward.explanation(),
                awkward.evidence(),
                awkward.remediation()));

        assertEquals(SarifReport.render(result), SarifReport.render(result, List.of(sourceRoot())));
    }

    @Test
    void rendersTheSameBytesOnEveryRunWithMappingActive() throws IOException {
        List<Path> roots = List.of(sourceRoot());
        VerificationResult result = atLine(SampleFindings.SOURCE_LINE);

        assertEquals(SarifReport.render(result, roots), SarifReport.render(result, roots));
    }

    /** A source tree that really holds the specimen's source file, at its package path. */
    private Path sourceRoot() throws IOException {
        Path root = workspace.resolve(SOURCE_ROOT);
        Path source = root.resolve(SampleFindings.SOURCE_PATH);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.acme.orders;\n");
        return root;
    }

    /** A root that exists and holds nothing, which is how a wrong root behaves. */
    private Path emptyRoot() throws IOException {
        return Files.createDirectories(workspace.resolve(GENERATED_ROOT));
    }

    private static VerificationResult atLine(int line) {
        return inSource(Optional.of(line));
    }

    private static VerificationResult inSource(Optional<Integer> line) {
        return SampleFindings.result(SampleFindings.missingMethodInSource(line));
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
