package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.List;
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
 * about a whole artifact, no {@code at runtime} line when nothing is predicted, no
 * {@code observed} lines without evidence, and no {@code next} line without a remediation. Blocks
 * are separated by a blank line and the report ends with one line of counts. Nothing here reads a
 * clock, a locale, or the file system, so the same findings always render the same bytes.
 */
final class HumanReport {
    private static final String SYMBOL = "symbol";
    private static final String CLASS = "class";
    private static final String ORIGIN = "from";
    private static final String RUNTIME = "at runtime";
    private static final String CAUSE = "cause";
    private static final String OBSERVED = "observed";
    private static final String NEXT = "next";
    private static final String FINDING = "finding";
    private static final String NOTHING_FOUND = "no findings";
    private static final String INDENT = "  ";
    private static final int VALUE_COLUMN = 15;
    private static final String CONTINUATION = " ".repeat(VALUE_COLUMN);

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
        return out.append(countLine(result.findings())).append('\n').toString();
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
        appendDetail(out, ORIGIN, finding.artifact().artifact());
        if (!finding.predictedError().equals(PredictedError.NONE)) {
            appendDetail(out, RUNTIME, finding.predictedError().value());
        }
        appendDetail(out, CAUSE, finding.explanation());
        appendEvidence(out, finding.evidence());
        appendNext(out, finding);
    }

    private static void appendEvidence(StringBuilder out, List<Evidence> evidence) {
        for (int index = 0; index < evidence.size(); index++) {
            String detail = evidence.get(index).detail();
            if (index == 0) {
                appendDetail(out, OBSERVED, detail);
            } else {
                out.append(CONTINUATION).append(detail).append('\n');
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
                .append(finding.remediation().getFirst().action())
                .append('\n');
    }

    private static void appendDetail(StringBuilder out, String label, String value) {
        StringBuilder line = new StringBuilder(INDENT).append(label).append(':');
        while (line.length() < VALUE_COLUMN) {
            line.append(' ');
        }
        out.append(line).append(value).append('\n');
    }

    private static String countLine(List<Finding> findings) {
        if (findings.isEmpty()) {
            return NOTHING_FOUND;
        }
        List<String> counts = new ArrayList<>();
        for (Severity severity : List.of(Severity.ERROR, Severity.WARNING, Severity.INFO)) {
            long occurrences = findings.stream().filter(finding -> finding.severity() == severity).count();
            if (occurrences > 0) {
                counts.add(quantity(occurrences, CanonicalName.of(severity)));
            }
        }
        counts.add(quantity(findings.size(), FINDING));
        StringBuilder line = new StringBuilder();
        for (String count : counts) {
            if (!line.isEmpty()) {
                line.append(',').append(' ');
            }
            line.append(count);
        }
        return line.toString();
    }

    private static String quantity(long occurrences, String noun) {
        String counted = occurrences + " " + noun;
        return occurrences == 1 ? counted : counted + 's';
    }
}
