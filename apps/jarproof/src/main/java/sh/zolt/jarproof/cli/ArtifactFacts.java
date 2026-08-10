package sh.zolt.jarproof.cli;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * The table of facts {@code inspect} shows a person.
 *
 * <p>One label per line, every value starting in the same column, in the order a reader asks the
 * questions -- what is this, how big is it, what does it carry, what was it compiled for, what does it
 * register, what does it vary by release:
 *
 * <pre>
 * artifact:         build/app.jar
 * entries:          27
 * classes:          24
 * nested-archives:  0
 * bytecode:         52:2, 61:22
 * services:         com.acme.spi.Codec
 * multi-release:    11, 17
 * </pre>
 *
 * <p>A {@code bytecode} value pairs a class file major version with how many classes declare it. A
 * {@code nested-archives} value above zero is the shape of an application archive carrying its own
 * dependencies, and the classes inside those archives are not part of the class count. A fact the
 * artifact does not have prints as {@code none} rather than vanishing, because an absent line reads as
 * a rendering bug while an empty one is an answer. Nothing here reads a clock, a locale, or the file
 * system, so the same summary always renders the same bytes.
 */
final class ArtifactFacts {
    private static final String ENTRIES = "entries";
    private static final String CLASSES = "classes";
    private static final String NESTED_ARCHIVES = "nested-archives";
    private static final String BYTECODE = "bytecode";
    private static final String SERVICES = "services";
    private static final String MULTI_RELEASE = "multi-release";
    private static final String NOTHING = "none";
    private static final int VALUE_COLUMN = 18;

    private ArtifactFacts() {
    }

    /**
     * Renders one artifact's facts as an aligned table.
     *
     * @param summary the facts the inspection read
     * @return the complete table, terminated by a single LF
     */
    static String render(ArtifactSummary summary) {
        StringBuilder out = new StringBuilder();
        appendFact(out, FindingJson.ARTIFACT, summary.artifact());
        appendFact(out, ENTRIES, String.valueOf(summary.entryCount()));
        appendFact(out, CLASSES, String.valueOf(summary.classCount()));
        appendFact(out, NESTED_ARCHIVES, String.valueOf(summary.nestedArchiveCount()));
        appendFact(out, BYTECODE, joined(summary.bytecodeLevels()));
        appendFact(out, SERVICES, joined(summary.declaredServices()));
        appendFact(out, MULTI_RELEASE, joined(summary.multiReleaseVersions()));
        return out.toString();
    }

    private static void appendFact(StringBuilder out, String label, String value) {
        StringBuilder line = new StringBuilder(label).append(':');
        while (line.length() < VALUE_COLUMN) {
            line.append(' ');
        }
        out.append(line).append(value).append('\n');
    }

    private static String joined(List<?> values) {
        StringBuilder line = new StringBuilder();
        for (Object value : values) {
            if (!line.isEmpty()) {
                line.append(',').append(' ');
            }
            line.append(value);
        }
        return line.isEmpty() ? NOTHING : line.toString();
    }
}
