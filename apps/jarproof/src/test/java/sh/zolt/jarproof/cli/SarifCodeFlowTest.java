package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The code flow a proving chain becomes, and the findings that get none.
 *
 * <p>The chain spellings here are written out as text rather than taken from the engine, because that
 * is exactly the coupling under test: the CLI recognises the prefix and the separator the engine
 * composes, and nothing in this member imports them. A unit test can only prove the recognition works
 * on text of that shape -- that the shape is still the shape the engine writes is pinned by the
 * integration test over a real {@code --scope reachable} run.
 */
final class SarifCodeFlowTest {
    private static final String PROVING_CHAIN = "reachable via: ";
    private static final String ENTRY = "a/B#one()V";
    private static final String REACHED = "c/D#two()V";
    private static final String ELISION = "... 7 hops ...";
    private static final String STEP_SEPARATOR = " -> ";
    private static final String CODE_FLOWS = "codeFlows";
    private static final String NOTED = "message: ";

    @Test
    void rendersAProvingChainAsACodeFlow() {
        String document = SarifReport.render(SampleFindings.result(reachedThrough(ENTRY + STEP_SEPARATOR + REACHED)));

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
                          "version": "0.0.1-alpha.1",
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
                            "markdown": "**missing method**\\u000A\\u000AThe resolved class declares no post().\\u000A\\u000A- reachable via: a/B\\\\#one()V -\\\\> c/D\\\\#two()V"
                          },
                          "locations": [
                            {
                              "physicalLocation": {
                                "artifactLocation": {
                                  "uri": "app.jar"
                                }
                              }
                            }
                          ],
                          "codeFlows": [
                            {
                              "threadFlows": [
                                {
                                  "locations": [
                                    {
                                      "location": {
                                        "logicalLocations": [
                                          {
                                            "fullyQualifiedName": "a/B#one()V",
                                            "kind": "function"
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "location": {
                                        "logicalLocations": [
                                          {
                                            "fullyQualifiedName": "c/D#two()V",
                                            "kind": "function"
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                              ]
                            }
                          ],
                          "properties": {
                            "evidence": [
                              "reachable via: a/B#one()V -> c/D#two()V"
                            ],
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
    void keepsAChainOfOneStepAsOneStep() {
        assertEquals(List.of(ENTRY), steps(reachedThrough(ENTRY)));
    }

    /** The elision a long chain renders in its own middle is a step with a note and no name. */
    @Test
    void carriesAnElidedMiddleAsAStepThatOnlyDescribesItself() {
        String chain = ENTRY + STEP_SEPARATOR + ELISION + STEP_SEPARATOR + REACHED;

        assertEquals(List.of(ENTRY, NOTED + ELISION, REACHED), steps(reachedThrough(chain)));
    }

    @Test
    void writesNoCodeFlowForALinkageFindingThatProvedNoChain() {
        String document = SarifReport.render(SampleFindings.result(SampleFindings.missingMethod()));

        assertFalse(document.contains(CODE_FLOWS), document);
    }

    @Test
    void writesNoCodeFlowForAFindingThatIsNotAboutLinkageAtAll() {
        String document = SarifReport.render(
                SampleFindings.result(SampleFindings.duplicateClass(), SampleFindings.splitPackage()));

        assertFalse(document.contains(CODE_FLOWS), document);
    }

    @Test
    void rendersTheSameBytesOnEveryRun() {
        VerificationResult result = SampleFindings.result(reachedThrough(ENTRY + STEP_SEPARATOR + REACHED));

        assertEquals(SarifReport.render(result), SarifReport.render(result));
    }

    /** A finding whose only evidence is the chain that proved its referencing method executes. */
    private static Finding reachedThrough(String chain) {
        return new Finding(
                FindingCode.of("JP1003"),
                Severity.ERROR,
                PredictedError.NO_SUCH_METHOD_ERROR,
                ArtifactLocation.ofArtifact(SampleFindings.APPLICATION),
                "com/acme/api/Ledger#post()V",
                "missing method",
                "The resolved class declares no post().",
                List.of(new Evidence(PROVING_CHAIN + chain)),
                List.of());
    }

    /**
     * Each step of the only code flow in a run, named by its logical location, or by its note when it
     * carries no name.
     */
    private static List<String> steps(Finding finding) {
        String document = SarifReport.render(SampleFindings.result(finding));
        Map<?, ?> run = object(array(object(JsonScanner.parse(document)).get("runs")).get(0));
        Map<?, ?> result = object(array(run.get("results")).get(0));
        Map<?, ?> flow = object(array(object(array(result.get(CODE_FLOWS)).get(0)).get("threadFlows")).get(0));
        List<String> described = new ArrayList<>();
        for (Object step : array(flow.get("locations"))) {
            described.add(described(object(object(step).get("location"))));
        }
        return List.copyOf(described);
    }

    private static String described(Map<?, ?> location) {
        Object named = location.get("logicalLocations");
        if (named == null) {
            return NOTED + object(location.get("message")).get("text");
        }
        return (String) object(array(named).get(0)).get("fullyQualifiedName");
    }

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        return (List<?>) value;
    }
}
