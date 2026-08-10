package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

final class ExitPlumbingTest {
    @Test
    void promisesTheDocumentedProcessStatuses() {
        assertEquals(0, ExitCode.CLEAN.status());
        assertEquals(1, ExitCode.FINDINGS.status());
        assertEquals(2, ExitCode.INVOCATION.status());
    }

    @Test
    void failsOnErrorsByDefaultAndIgnoresQuieterFindings() {
        assertEquals(
                ExitCode.FINDINGS,
                FailureThreshold.ERROR.verdict(SampleFindings.result(SampleFindings.missingMethod())));
        assertEquals(
                ExitCode.CLEAN,
                FailureThreshold.ERROR.verdict(
                        SampleFindings.result(SampleFindings.duplicateClass(), SampleFindings.splitPackage())));
    }

    @Test
    void failsOnWarningsWhenAsked() {
        assertEquals(
                ExitCode.FINDINGS,
                FailureThreshold.WARNING.verdict(SampleFindings.result(SampleFindings.duplicateClass())));
        assertEquals(
                ExitCode.CLEAN,
                FailureThreshold.WARNING.verdict(SampleFindings.result(SampleFindings.splitPackage())));
    }

    @Test
    void neverFailsWhenTheThresholdIsNever() {
        VerificationResult everything = SampleFindings.result(
                SampleFindings.missingMethod(), SampleFindings.duplicateClass(), SampleFindings.splitPackage());

        assertEquals(ExitCode.CLEAN, FailureThreshold.NEVER.verdict(everything));
    }

    @Test
    void reportsACleanRunAsClean() {
        VerificationResult nothing = SampleFindings.result();

        assertEquals(ExitCode.CLEAN, FailureThreshold.ERROR.verdict(nothing));
        assertEquals(ExitCode.CLEAN, FailureThreshold.WARNING.verdict(nothing));
        assertEquals(ExitCode.CLEAN, FailureThreshold.NEVER.verdict(nothing));
    }

    @Test
    void parsesEveryThresholdAndRejectsAnythingElse() {
        assertEquals(Optional.of(FailureThreshold.ERROR), FailureThreshold.parse("error"));
        assertEquals(Optional.of(FailureThreshold.WARNING), FailureThreshold.parse("warning"));
        assertEquals(Optional.of(FailureThreshold.NEVER), FailureThreshold.parse("never"));
        assertTrue(FailureThreshold.parse("ERROR").isEmpty());
        assertTrue(FailureThreshold.parse("sometimes").isEmpty());
    }

    @Test
    void spellsEveryVocabularyConstantInLowerCase() {
        assertEquals("info", CanonicalName.of(Severity.INFO));
        assertEquals("application", CanonicalName.of(Scope.APPLICATION));
        assertEquals("disabled", CanonicalName.of(PreviewMode.DISABLED));
        assertEquals("sarif", CanonicalName.of(ReportFormat.SARIF));
    }

    @Test
    void resolvesACanonicalSpellingBackToItsConstant() {
        assertEquals(Optional.of(Severity.ERROR), CanonicalName.parse(Severity.class, "error"));
        assertTrue(CanonicalName.parse(Severity.class, "fatal").isEmpty());
    }

    @Test
    void countsANounTheWayEnglishDoes() {
        assertEquals("1 finding", Quantity.of(1, "finding"));
        assertEquals("2 findings", Quantity.of(2, "finding"));
        assertEquals("0 findings", Quantity.of(0, "finding"));
        assertEquals("1 baseline entry", Quantity.of(1, "baseline entry", "baseline entries"));
        assertEquals("3 baseline entries", Quantity.of(3, "baseline entry", "baseline entries"));
    }

    /**
     * A write can fail in ways the file system does not classify, and those degrade to the runtime's
     * own sentence rather than to a class name -- the reason every command reports a failed write
     * through one place.
     */
    @Test
    void reportsAWriteFailureThatNamesNoFile() {
        StringWriter err = new StringWriter();

        int status = FailedInvocation.unwritable(new PrintWriter(err, true), new IOException("no space left"));

        assertEquals(2, status);
        assertEquals("no space left\n", err.toString());
    }
}
