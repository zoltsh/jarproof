package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationResult;

final class HumanReportTest {
    @Test
    void rendersTheSpecimenFromTheDesign() {
        String report = HumanReport.render(SampleFindings.result(SampleFindings.missingMethod()));

        assertEquals(
                """
                error JP1003: missing method
                  symbol:      com.google.common.base.Preconditions.checkArgument(boolean, String, Object)
                  class:       com/acme/orders/OrderValidator.class
                  from:        app.jar
                  at runtime:  NoSuchMethodError
                  cause:       guava-18.0.jar declares no checkArgument(boolean, String, Object) on Preconditions.
                  observed:    selected guava-18.0.jar from lib/*
                               app.jar was compiled against a newer guava

                next: align the runtime classpath with the version used to compile app.jar

                1 error, 1 finding
                """,
                report);
    }

    @Test
    void citesTheSourceFileAndLineTheClassFileRecorded() {
        String report = HumanReport.render(SampleFindings.result(
                SampleFindings.missingMethodInSource(Optional.of(SampleFindings.SOURCE_LINE))));

        assertEquals(
                """
                error JP1003: missing method
                  symbol:      com.google.common.base.Preconditions.checkArgument(boolean, String, Object)
                  class:       com/acme/orders/OrderValidator.class
                  source:      OrderValidator.java:42
                  from:        app.jar
                  at runtime:  NoSuchMethodError
                  cause:       guava-18.0.jar declares no checkArgument(boolean, String, Object) on Preconditions.
                  observed:    selected guava-18.0.jar from lib/*
                               app.jar was compiled against a newer guava

                next: align the runtime classpath with the version used to compile app.jar

                1 error, 1 finding
                """,
                report);
    }

    @Test
    void citesTheSourceFileAloneWhenTheClassFileRecordedNoLine() {
        String report = HumanReport.render(
                SampleFindings.result(SampleFindings.missingMethodInSource(Optional.empty())));

        assertTrue(report.contains("\n  source:      OrderValidator.java\n"), report);
    }

    @Test
    void separatesFindingsAndEndsWithOneCountLine() {
        String report = HumanReport.render(SampleFindings.result(
                SampleFindings.missingMethod(), SampleFindings.duplicateClass(), SampleFindings.splitPackage()));

        assertTrue(report.contains("\n\nwarning JP2002: duplicate class with differing bytecode\n"), report);
        assertTrue(report.contains("\n\ninfo JP2003: split package across artifacts\n"), report);
        assertTrue(report.endsWith("\n1 error, 1 warning, 1 info, 3 findings\n"), report);
    }

    @Test
    void saysSoWhenNothingWasFound() {
        assertEquals("no findings\n", HumanReport.render(SampleFindings.result()));
    }

    /**
     * The line that closes a clean run has to distinguish a verified classpath from a run that opened
     * nothing, and the numbers are the only thing that can do it.
     */
    @Test
    void saysWhatItExaminedToFindNothing() {
        assertEquals(
                "no findings — analyzed 5000 classes across 40 artifacts\n",
                HumanReport.render(SampleFindings.examined(5000, 40)));
    }

    @Test
    void countsOneClassAndOneArtifactAsOneEach() {
        assertEquals(
                "no findings — analyzed 1 class across 1 artifact\n",
                HumanReport.render(SampleFindings.examined(1, 1)));
    }

    /** A position that presents no classes was still read, so the run says what it read. */
    @Test
    void saysSoWhenTheArtifactsItReadPresentedNoClassesAtAll() {
        assertEquals(
                "no findings — analyzed 0 classes across 2 artifacts\n",
                HumanReport.render(SampleFindings.examined(0, 2)));
    }

    /** The count line of a report that found something already says what a reader needs. */
    @Test
    void leavesTheCountLineAloneWhenThereAreFindings() {
        String report = HumanReport.render(SampleFindings.examined(5000, 40, SampleFindings.splitPackage()));

        assertTrue(report.endsWith("\n1 info, 1 finding\n"), report);
        assertFalse(report.contains("analyzed"), report);
    }

    @Test
    void omitsTheSourceLineForAClassCompiledWithoutDebugInformation() {
        String report = HumanReport.render(SampleFindings.result(SampleFindings.missingMethod()));

        assertFalse(report.contains("source:"), report);
    }

    @Test
    void omitsDetailLinesTheFindingDoesNotHave() {
        String report = HumanReport.render(SampleFindings.result(SampleFindings.splitPackage()));

        assertEquals(
                """
                info JP2003: split package across artifacts
                  symbol:      com.acme.orders
                  from:        lib/extras.jar
                  cause:       The package is assembled from more than one artifact, which a classpath allows.

                1 info, 1 finding
                """,
                report);
        assertFalse(report.contains("class:"), report);
        assertFalse(report.contains("at runtime:"), report);
        assertFalse(report.contains("observed:"), report);
        assertFalse(report.contains("next:"), report);
    }

    @Test
    void pluralisesEveryCount() {
        Finding error = SampleFindings.missingMethod();
        Finding warning = SampleFindings.duplicateClass();

        String report = HumanReport.render(SampleFindings.result(error, error, warning, warning));

        assertTrue(report.endsWith("\n2 errors, 2 warnings, 4 findings\n"), report);
    }

    @Test
    void startsEveryValueInTheSameColumn() {
        String report = HumanReport.render(SampleFindings.result(SampleFindings.missingMethod()));

        List<String> detailLines = report.lines().filter(line -> line.startsWith("  ")).toList();
        assertEquals(7, detailLines.size(), report);
        for (String line : detailLines) {
            assertEquals(' ', line.charAt(14), line);
            assertFalse(line.charAt(15) == ' ', line);
        }
    }

    @Test
    void rendersTheSameBytesOnEveryRun() {
        VerificationResult result = SampleFindings.result(
                SampleFindings.missingMethod(), SampleFindings.duplicateClass(), SampleFindings.splitPackage());

        assertEquals(HumanReport.render(result), HumanReport.render(result));
    }

    @Test
    void ignoresTheDefaultLocale() {
        VerificationResult result = SampleFindings.result(SampleFindings.splitPackage());
        String expected = HumanReport.render(result);
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(expected, HumanReport.render(result));
        } finally {
            Locale.setDefault(original);
        }
    }
}
