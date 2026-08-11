package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

final class JsonReportTest {
    @Test
    void rendersTheVersionedEnvelopeInContractOrder() {
        VerificationResult result = SampleFindings.result(
                SampleFindings.missingMethod(), SampleFindings.duplicateClass(), SampleFindings.splitPackage());

        String document = JsonReport.render(SampleFindings.request(), result);

        assertEquals(
                """
                {
                  "jarproofJsonVersion": "1",
                  "tool": {
                    "name": "jarproof",
                    "version": "0.0.1-SNAPSHOT"
                  },
                  "request": {
                    "targetJava": 17,
                    "preview": "disabled",
                    "scope": "application"
                  },
                  "findings": [
                    {
                      "code": "JP1003",
                      "severity": "error",
                      "predictedError": "NoSuchMethodError",
                      "artifact": {
                        "artifact": "app.jar",
                        "classEntry": "com/acme/orders/OrderValidator.class"
                      },
                      "subject": "com/google/common/base/Preconditions#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V",
                      "summary": "missing method",
                      "explanation": "guava-18.0.jar declares no checkArgument(boolean, String, Object) on Preconditions.",
                      "evidence": [
                        "selected guava-18.0.jar from lib/*",
                        "app.jar was compiled against a newer guava"
                      ],
                      "remediation": [
                        "align the runtime classpath with the version used to compile app.jar",
                        "or recompile app.jar against guava-18.0.jar"
                      ]
                    },
                    {
                      "code": "JP2002",
                      "severity": "warning",
                      "predictedError": "None",
                      "artifact": {
                        "artifact": "lib/commons-io-2.4.jar"
                      },
                      "subject": "org/apache/commons/io/IOUtils",
                      "summary": "duplicate class with differing bytecode",
                      "explanation": "Two artifacts declare the class with different content, so one definition is dead.",
                      "evidence": [
                        "also declared by lib/commons-io-2.11.0.jar"
                      ],
                      "remediation": [
                        "remove one of the two commons-io artifacts"
                      ]
                    },
                    {
                      "code": "JP2003",
                      "severity": "info",
                      "predictedError": "None",
                      "artifact": {
                        "artifact": "lib/extras.jar"
                      },
                      "subject": "com/acme/orders",
                      "summary": "split package across artifacts",
                      "explanation": "The package is assembled from more than one artifact, which a classpath allows.",
                      "evidence": [],
                      "remediation": []
                    }
                  ],
                  "summary": {
                    "info": 1,
                    "warning": 1,
                    "error": 1,
                    "total": 3
                  }
                }
                """,
                document);
    }

    @Test
    void addsTheOptionalSourceMembersAfterTheClassEntry() {
        String document = JsonReport.render(SampleFindings.request(), located(SampleFindings.SOURCE_LINE));

        assertTrue(document.contains("""
                      "artifact": {
                        "artifact": "app.jar",
                        "classEntry": "com/acme/orders/OrderValidator.class",
                        "sourceFile": "OrderValidator.java",
                        "line": 42
                      },
                """), document);
    }

    @Test
    void omitsTheLineMemberWhenTheClassFileRecordedNone() {
        String document = JsonReport.render(
                SampleFindings.request(),
                SampleFindings.result(SampleFindings.missingMethodInSource(Optional.empty())));

        assertTrue(document.contains("\"sourceFile\": \"OrderValidator.java\"\n"), document);
        assertFalse(document.contains("\"line\""), document);
    }

    @Test
    void leavesBothSourceMembersOutOfAFindingThatNamesNoSourceFile() {
        String document = JsonReport.render(
                SampleFindings.request(), SampleFindings.result(SampleFindings.missingMethod()));

        assertFalse(document.contains("\"sourceFile\""), document);
        assertFalse(document.contains("\"line\""), document);
    }

    @Test
    void countsEverySeverityIncludingTheOnesThatDidNotOccur() {
        String document = JsonReport.render(
                SampleFindings.request(), SampleFindings.result(SampleFindings.missingMethod()));

        assertTrue(
                document.endsWith("\n  \"summary\": {\n    \"info\": 0,\n    \"warning\": 0,\n"
                        + "    \"error\": 1,\n    \"total\": 1\n  }\n}\n"),
                document);
    }

    /** The additive keys follow the ones a consumer of JSON v1 already reads, never before them. */
    @Test
    void reportsWhatTheRunExaminedAfterTheCountsItAlreadyCarried() {
        String document = JsonReport.render(
                SampleFindings.request(), SampleFindings.examined(5000, 40, SampleFindings.missingMethod()));

        assertTrue(
                document.endsWith("\n  \"summary\": {\n    \"info\": 0,\n    \"warning\": 0,\n"
                        + "    \"error\": 1,\n    \"total\": 1,\n    \"analyzedClasses\": 5000,\n"
                        + "    \"analyzedArtifacts\": 40\n  }\n}\n"),
                document);
    }

    /** A result that states no tallies omits both keys rather than claiming a run examined nothing. */
    @Test
    void leavesBothTallyKeysOutOfAResultThatStatesNone() {
        String document = JsonReport.render(
                SampleFindings.request(), SampleFindings.result(SampleFindings.missingMethod()));

        assertFalse(document.contains("\"analyzedClasses\""), document);
        assertFalse(document.contains("\"analyzedArtifacts\""), document);
    }

    /** Zero classes read from artifacts that were really opened is a tally, not an absent one. */
    @Test
    void reportsArtifactsThatPresentedNoClassesAsATallyOfTheirOwn() {
        String document = JsonReport.render(SampleFindings.request(), SampleFindings.examined(0, 2));

        assertTrue(document.contains("\"analyzedClasses\": 0,\n    \"analyzedArtifacts\": 2\n"), document);
    }

    @Test
    void escapesAwkwardFindingTextWithoutTouchingTheRest() {
        Finding awkward = new Finding(
                FindingCode.of("JP3004"),
                Severity.ERROR,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact("lib\\odd.jar"),
                "com/acme/Quote",
                "unparseable class file",
                "The entry says \"hello\"\nand then stops.",
                List.of(new Evidence("ship " + Character.toString(0x1F680) + (char) 0x2028)),
                List.of());

        String document = JsonReport.render(SampleFindings.request(), SampleFindings.result(awkward));

        assertTrue(document.contains("\"artifact\": \"lib\\\\odd.jar\""), document);
        assertTrue(document.contains("\"The entry says \\\"hello\\\"\\u000Aand then stops.\""), document);
        assertTrue(document.contains("ship " + Character.toString(0x1F680) + (char) 0x2028), document);
    }

    @Test
    void writesLineFeedsOnlyAndEndsWithExactlyOne() {
        String document = JsonReport.render(
                SampleFindings.request(), SampleFindings.result(SampleFindings.splitPackage()));

        assertEquals(-1, document.indexOf('\r'), document);
        assertTrue(document.endsWith("}\n"), document);
    }

    @Test
    void rendersTheSameBytesOnEveryRun() {
        VerificationResult result = SampleFindings.result(SampleFindings.missingMethod());

        assertEquals(
                JsonReport.render(SampleFindings.request(), result),
                JsonReport.render(SampleFindings.request(), result));
    }

    private static VerificationResult located(int line) {
        return SampleFindings.result(SampleFindings.missingMethodInSource(Optional.of(line)));
    }
}
