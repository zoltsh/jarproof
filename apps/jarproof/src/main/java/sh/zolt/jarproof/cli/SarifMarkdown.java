package sh.zolt.jarproof.cli;

import java.util.List;
import java.util.StringJoiner;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Remediation;

/**
 * The {@code markdown} member of a SARIF result message: the whole finding, formatted.
 *
 * <p>{@code text} stays the bare summary, because that is the one member every consumer reads and
 * some render nothing else. {@code markdown} is what a code-scanning UI shows when it can: the summary
 * as a bold first line, the explanation as its own paragraph, the evidence as a bulleted list, and the
 * first remediation after a bold {@code next:} label -- the same five facts, in the same order, that
 * the human report prints. A finding with no evidence contributes no list, and a finding with no
 * remediation contributes no {@code next:} line, rather than an empty one.
 *
 * <h2>Escaping</h2>
 *
 * <p>Finding text is data, and jarproof's data is bytecode: {@code <init>}, {@code [Ljava/lang/String;},
 * {@code lib/*}, {@code A -> B}. Rendered as markdown, {@code <init>} is an unknown HTML tag and
 * disappears, and a pair of asterisks turns a classpath wildcard into emphasis. So every character of
 * data is escaped by exactly one rule, applied everywhere and only to data: each occurrence of
 * <code>\</code>, <code>`</code>, {@code *}, {@code _}, {@code [}, {@code ]}, {@code <}, {@code >}, or
 * {@code #} is preceded by a backslash. Nothing else is touched and no character is escaped by
 * position, so the same words escape the same way in the bold line, the paragraph, and a bullet.
 *
 * <p>That set is the characters that change inline rendering wherever they sit -- emphasis, code spans,
 * links, raw HTML and autolinks -- plus {@code #}, which would make a heading of an explanation that
 * happened to begin with one. It deliberately stops there. A leading {@code -}, {@code +}, or
 * {@code 1.} in an explanation still renders as a one-item list, which misplaces a bullet and loses no
 * words, and escaping those characters everywhere would put a backslash inside every artifact version
 * and every arrow of a proving chain. The markup jarproof writes itself -- the asterisks, the
 * {@code -} of each bullet, the blank lines -- is never escaped, which is the whole point of escaping
 * the data instead of the result.
 *
 * <p>Nothing here reads a clock, a locale, or the file system: the same finding renders the same bytes,
 * with LF line endings like every other document this member writes.
 */
final class SarifMarkdown {
    private static final String MARKDOWN = "markdown";
    private static final String STRONG = "**";
    private static final String BULLET = "- ";
    private static final String NEXT_STEP = "**next:** ";
    private static final String PARAGRAPH = "\n\n";
    private static final String ESCAPED = "\\`*_[]<>#";
    private static final char ESCAPE = '\\';

    private SarifMarkdown() {
    }

    /**
     * Writes the {@code markdown} member of the message object already open.
     *
     * @param json the writer positioned inside a result's message object
     * @param finding the finding to render
     */
    static void append(JsonText json, Finding finding) {
        json.name(MARKDOWN).value(rendered(finding));
    }

    private static String rendered(Finding finding) {
        StringBuilder out = new StringBuilder(STRONG).append(escaped(finding.summary())).append(STRONG);
        out.append(PARAGRAPH).append(escaped(finding.explanation()));
        appendEvidence(out, finding.evidence());
        appendNext(out, finding.remediation());
        return out.toString();
    }

    private static void appendEvidence(StringBuilder out, List<Evidence> evidence) {
        if (evidence.isEmpty()) {
            return;
        }
        StringJoiner bullets = new StringJoiner("\n");
        for (Evidence detail : evidence) {
            bullets.add(BULLET + escaped(detail.detail()));
        }
        out.append(PARAGRAPH).append(bullets);
    }

    private static void appendNext(StringBuilder out, List<Remediation> remediation) {
        if (remediation.isEmpty()) {
            return;
        }
        out.append(PARAGRAPH).append(NEXT_STEP).append(escaped(remediation.get(0).action()));
    }

    /** Neutralises every markdown-significant character of one piece of data. */
    private static String escaped(String data) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < data.length(); index++) {
            char character = data.charAt(index);
            if (ESCAPED.indexOf(character) >= 0) {
                out.append(ESCAPE);
            }
            out.append(character);
        }
        return out.toString();
    }
}
