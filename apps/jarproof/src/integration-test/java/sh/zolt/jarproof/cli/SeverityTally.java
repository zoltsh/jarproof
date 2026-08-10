package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import sh.zolt.jarproof.cli.CorpusCheck.Reported;

/**
 * How many findings of each severity a report carried, per diagnostic code, and what moved.
 *
 * <p>A false-positive ceiling is only useful if a breach explains itself. Asserting a total says a
 * number moved; this says which check moved it, which is the first thing anyone asks. So a tally is
 * counted per severity and per code-and-severity pair at once, and comparing two of them yields one
 * line per count that disagrees rather than two opaque numbers.
 *
 * <p>Counts are taken from parsed findings, never from rendered lines: a tally must not be able to
 * drift because a writer changed its wording, and it must not be able to pass because a run wrote
 * nothing at all. The parsing side refuses a missing report outright ({@link CorpusCheck#findings()}),
 * so an empty tally here always means a run that really did analyse and really found nothing.
 */
final class SeverityTally {
    private static final List<String> SEVERITIES = List.of("error", "warning", "info");
    private static final Map<String, String> PLURALS =
            Map.of("error", "errors", "warning", "warnings", "info", "info");

    private final Map<String, Integer> counts;

    private SeverityTally(Map<String, Integer> counts) {
        this.counts = Map.copyOf(counts);
    }

    /**
     * Counts what a run actually reported.
     *
     * @param findings the parsed findings of one report, which may legitimately be none
     * @return the severity totals and the per-code breakdown behind them
     */
    static SeverityTally of(List<Reported> findings) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Integer> codes = new TreeMap<>();
        for (Reported finding : findings) {
            totals.merge(plural(finding.severity()), 1, Integer::sum);
            codes.merge(finding.code() + " " + plural(finding.severity()), 1, Integer::sum);
        }
        totals.putAll(codes);
        return new SeverityTally(totals);
    }

    /**
     * States what a run is expected to report.
     *
     * @param errors the release-blocking count, which DESIGN section 4 pins at zero
     * @param warnings the accepted library-only evidence count
     * @param info the accepted advisory count
     * @param byCode each {@code "JP1003 warnings"} pair and its count, which must sum to the totals
     * @return the pinned tally
     */
    static SeverityTally pinning(int errors, int warnings, int info, Map<String, Integer> byCode) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(plural("error"), errors);
        counts.put(plural("warning"), warnings);
        counts.put(plural("info"), info);
        counts.putAll(new TreeMap<>(byCode));
        return new SeverityTally(counts);
    }

    /**
     * Returns every count that disagrees between this pinned tally and what a run reported.
     *
     * @param observed the tally of an actual run
     * @return one line per disagreement, severity totals first and codes in code order, or none
     */
    List<String> diff(SeverityTally observed) {
        List<String> drift = new ArrayList<>();
        for (String key : union(observed)) {
            int expected = counts.getOrDefault(key, 0);
            int saw = observed.counts.getOrDefault(key, 0);
            if (expected != saw) {
                drift.add(key + ": expected " + expected + ", saw " + saw);
            }
        }
        return List.copyOf(drift);
    }

    /** Severity totals in severity order, then every code either side mentions, in code order. */
    private Set<String> union(SeverityTally observed) {
        Set<String> keys = new LinkedHashSet<>();
        for (String severity : SEVERITIES) {
            keys.add(plural(severity));
        }
        Set<String> codes = new TreeSet<>(counts.keySet());
        codes.addAll(observed.counts.keySet());
        codes.removeAll(keys);
        keys.addAll(codes);
        return keys;
    }

    /**
     * The reading name of a severity, so a diff line reads as prose. An unrecognised severity keeps
     * its own text rather than vanishing, because a report naming a severity this harness does not
     * know is itself the finding.
     */
    private static String plural(String severity) {
        return PLURALS.getOrDefault(severity, severity);
    }
}
