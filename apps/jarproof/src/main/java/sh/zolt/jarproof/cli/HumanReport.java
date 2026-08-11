package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * The compiler-style report a person reads. This is the product's face, so its shape is fixed and
 * golden-tested rather than adjusted per finding.
 *
 * <p>Each finding is one block. The first line is the severity, the diagnostic code, and the
 * one-line summary. Indented detail lines follow, each label padded so every value starts in the
 * same column:
 *
 * <pre>
 * error JP1003: missing method
 *   symbol:      com.google.common.base.Preconditions.checkArgument(boolean, String, Object)
 *   class:       com/acme/orders/OrderValidator.class
 *   source:      OrderValidator.java:42
 *   from:        app.jar
 *   at runtime:  NoSuchMethodError
 *   cause:       &lt;the finding's explanation&gt;
 *   observed:    &lt;first evidence detail&gt;
 *                &lt;further evidence details, aligned&gt;
 *
 * next: align the runtime classpath with the version used to compile app.jar
 * </pre>
 *
 * <p>A line is omitted rather than emitted empty: there is no {@code class} line for a finding
 * about a whole artifact, no {@code source} line for a class compiled without debug information, no
 * {@code at runtime} line when nothing is predicted, no {@code observed} lines without evidence, and
 * no {@code next} line without a remediation. The {@code source} line names the file the class was
 * compiled from and, when the class file records one, the line the reference is written on; it is the
 * class file's own word, so no path and no source root take part in it. Blocks are separated by a
 * blank line and the report ends with one line of counts. Nothing here reads a clock, a locale, or the
 * file system, so the same findings always render the same bytes.
 *
 * <p>A run that found nothing says what it examined:
 *
 * <pre>
 * no findings — analyzed 5000 classes across 40 artifacts
 * </pre>
 *
 * <p>Without those numbers the most reassuring line jarproof prints is also the line a broken
 * invocation prints, and a reader has no way to tell a verified classpath from a classpath nobody
 * opened. A result that states no tallies renders the bare {@code no findings} instead of inventing
 * them. A report that does carry findings gains nothing from the pair: its count line already says
 * what a reader needs, and the findings themselves name the artifacts they came from.
 */
final class HumanReport {
    private static final String SYMBOL = "symbol";
    private static final String CLASS = "class";
    private static final String SOURCE = "source";
    private static final String ORIGIN = "from";
    private static final String RUNTIME = "at runtime";
    private static final String CAUSE = "cause";
    private static final String OBSERVED = "observed";
    private static final String NEXT = "next";
    private static final String FINDING = "finding";
    private static final String NOTHING_FOUND = "no findings";
    private static final String INDENT = "  ";
    private static final int VALUE_COLUMN = 15;

    private HumanReport() {
    }

    /**
     * Renders every finding, then one line of counts.
     *
     * @param result the findings the run produced, in the order given
     * @return the complete report, terminated by a single LF
     */
    static String render(VerificationResult result) {
        StringBuilder out = new StringBuilder();
        for (Finding finding : result.findings()) {
            appendFinding(out, finding);
            out.append('\n');
        }
        return out.append(countLine(result)).append('\n').toString();
    }

    private static void appendFinding(StringBuilder out, Finding finding) {
        out.append(CanonicalName.of(finding.severity()))
                .append(' ')
                .append(finding.code().value())
                .append(':')
                .append(' ')
                .append(finding.summary())
                .append('\n');
        appendDetail(out, SYMBOL, SymbolText.readable(finding.subject()));
        finding.artifact().classEntry().ifPresent(entry -> appendDetail(out, CLASS, entry));
        cited(finding.artifact()).ifPresent(source -> appendDetail(out, SOURCE, source));
        appendDetail(out, ORIGIN, finding.artifact().artifact());
        if (!finding.predictedError().equals(PredictedError.NONE)) {
            appendDetail(out, RUNTIME, finding.predictedError().value());
        }
        appendDetail(out, CAUSE, finding.explanation());
        appendEvidence(out, finding.evidence());
        appendNext(out, finding);
    }

    /**
     * The source coordinates as a reader would cite them.
     *
     * @param location where the finding lives
     * @return the file and its line, the file alone when no line is recorded, or nothing at all
     */
    private static Optional<String> cited(ArtifactLocation location) {
        return location.sourceFile()
                .map(file -> location.line().map(line -> file + ':' + line).orElse(file));
    }

    private static void appendEvidence(StringBuilder out, List<Evidence> evidence) {
        String continuation = " ".repeat(VALUE_COLUMN);
        for (int index = 0; index < evidence.size(); index++) {
            String detail = evidence.get(index).detail();
            if (index == 0) {
                appendDetail(out, OBSERVED, detail);
            } else {
                out.append(continuation).append(detail).append('\n');
            }
        }
    }

    private static void appendNext(StringBuilder out, Finding finding) {
        if (finding.remediation().isEmpty()) {
            return;
        }
        out.append('\n')
                .append(NEXT)
                .append(':')
                .append(' ')
                .append(finding.remediation().get(0).action())
                .append('\n');
    }

    private static void appendDetail(StringBuilder out, String label, String value) {
        StringBuilder line = new StringBuilder(INDENT).append(label).append(':');
        while (line.length() < VALUE_COLUMN) {
            line.append(' ');
        }
        out.append(line).append(value).append('\n');
    }

    private static String countLine(VerificationResult result) {
        List<Finding> findings = result.findings();
        if (findings.isEmpty()) {
            return NOTHING_FOUND + examined(result);
        }
        List<String> counts = new ArrayList<>();
        for (Severity severity : List.of(Severity.ERROR, Severity.WARNING, Severity.INFO)) {
            long occurrences = findings.stream().filter(finding -> finding.severity() == severity).count();
            if (occurrences > 0) {
                counts.add(Quantity.of(occurrences, CanonicalName.of(severity)));
            }
        }
        counts.add(Quantity.of(findings.size(), FINDING));
        StringBuilder line = new StringBuilder();
        for (String count : counts) {
            if (!line.isEmpty()) {
                line.append(',').append(' ');
            }
            line.append(count);
        }
        return line.toString();
    }

    /**
     * What the run examined, as the tail of a line that found nothing.
     *
     * <p>Both counts zero means a result that states no tallies rather than a run that opened
     * nothing, so nothing is appended and the line stays the bare form it has always been. Every run
     * the engine completes reads at least the application it was given, so it always has a number.
     *
     * <p>The sentence is spelt here rather than hoisted into constants because it is one-off prose
     * that belongs beside the behaviour it explains, and because {@code classes} already has its
     * named home in the {@code inspect} renderer -- one literal, one place, so this one composes the
     * irregular plural from the label above instead of spelling it a second time.
     *
     * @param result the completed run
     * @return the tail naming the classes and artifacts examined, or empty text
     */
    private static String examined(VerificationResult result) {
        int classes = result.analyzedClassCount();
        int artifacts = result.analyzedArtifactCount();
        if (classes == 0 && artifacts == 0) {
            return "";
        }
        return " — analyzed "
                + Quantity.of(classes, CLASS, CLASS + "es")
                + " across "
                + Quantity.of(artifacts, FindingJson.ARTIFACT);
    }
}
