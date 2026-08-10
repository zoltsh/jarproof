package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One {@code jarproof check} run over corpus artifacts, and the findings it reported.
 *
 * <p>Runs go through the process entry point rather than the engine, so an assertion covers the
 * whole product a user invokes: flag parsing, the analysis, the machine report, and the exit status.
 * Machine runs are rooted at the workspace so every artifact path in a report is workspace-relative
 * and an expectation can be written out in full instead of being pattern-matched.
 *
 * <p>The report is read back with the CLI's own scanner, which is the point of living in this
 * package: a finding is asserted as parsed values, so a whitespace change in the writer cannot make
 * a linkage assertion fail and a renamed member cannot make one silently pass.
 */
final class CorpusCheck {
    private static final String NO_REPORT = "The check wrote no report, so it never analysed anything;"
            + " it exited with ";

    private final int exitCode;
    private final String err;
    private final String report;

    private CorpusCheck(int exitCode, String err, String report) {
        this.exitCode = exitCode;
        this.err = err;
        this.report = report;
    }

    /**
     * Runs a check that writes a canonical JSON report, measured against Java 17.
     *
     * <p>The report goes to the named file, so the output stream stays empty and only the diagnostic
     * stream is worth keeping: that is where a baseline note or a refusal is written.
     *
     * @param report the file the report is written to
     * @param arguments the flags describing what to verify
     * @return the finished run
     */
    static CorpusCheck reporting(Path report, List<String> arguments) {
        List<String> line = new ArrayList<>(List.of(CorpusCommand.CHECK));
        line.addAll(arguments);
        line.addAll(List.of(
                CorpusCommand.TARGET_JAVA, CorpusCommand.JAVA_17,
                CorpusCommand.FORMAT, CorpusCommand.JSON,
                CorpusCommand.OUTPUT, report.toString(),
                CorpusCommand.PATH_ROOT, FixtureCorpus.workspaceRoot().toString()));
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exitCode = Main.execute(
                new PrintWriter(out, true), new PrintWriter(err, true), line.toArray(new String[0]));
        return new CorpusCheck(exitCode, text(err.toString()), written(report));
    }

    /** Returns the process status the run ended with. */
    int exitCode() {
        return exitCode;
    }

    /** Returns everything the run wrote to the diagnostic stream. */
    String err() {
        return err;
    }

    /** Returns the machine report text, byte for byte as written. */
    String report() {
        return report;
    }

    /**
     * Returns every finding the report carries, in the order the engine produced them.
     *
     * <p>A run that wrote no report at all is refused here rather than answered with an empty list,
     * because the two are indistinguishable to an assertion that a check found nothing, and a
     * harness that reports success for a run that never happened is worse than no harness.
     *
     * @return the findings, which may legitimately be none
     * @throws IllegalStateException when the run produced no report
     */
    List<Reported> findings() {
        if (report.isEmpty()) {
            throw new IllegalStateException(NO_REPORT + exitCode + ": " + err);
        }
        List<Reported> findings = new ArrayList<>();
        for (Object element : array(object(JsonScanner.parse(report)).get("findings"))) {
            findings.add(reported(object(element)));
        }
        return List.copyOf(findings);
    }

    /** Returns each finding as its code and subject, which identifies it without quoting prose. */
    List<String> signatures() {
        return findings().stream().map(Reported::signature).toList();
    }

    /** Returns only the findings that fail a release gate. */
    List<Reported> errors() {
        return findings().stream().filter(finding -> "error".equals(finding.severity())).toList();
    }

    /** A run refused before it could write anything leaves no report, and no findings with it. */
    private static String written(Path report) {
        if (!Files.isRegularFile(report)) {
            return "";
        }
        try {
            return text(Files.readString(report));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Reported reported(Map<?, ?> finding) {
        return new Reported(
                (String) finding.get("code"),
                (String) finding.get("severity"),
                (String) finding.get("predictedError"),
                (String) object(finding.get("artifact")).get("artifact"),
                (String) finding.get("subject"));
    }

    private static String text(String written) {
        return written.replace("\r\n", "\n");
    }

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        return (List<?>) value;
    }

    /** The fields of one finding an execution assertion measures. */
    record Reported(String code, String severity, String predictedError, String artifact, String subject) {
        /** Returns the code and subject, which name the finding without repeating its prose. */
        String signature() {
            return code + " " + subject;
        }
    }
}
