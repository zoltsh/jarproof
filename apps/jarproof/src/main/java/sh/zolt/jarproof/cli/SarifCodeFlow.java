package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.Evidence;

/**
 * The {@code codeFlows} member of a SARIF result: the chain that proved a finding executes.
 *
 * <p>{@code --scope reachable} answers the question a reader asks first -- why is the broken method
 * reached at all -- with one evidence line naming every member from the entry surface to the
 * referencing method. SARIF has a structure for exactly that shape, and code-scanning UIs render it as
 * a path a reader steps through: one code flow, one thread flow, and one thread-flow location per
 * member, in chain order. Written as a code flow the proof is navigable; left as prose it is a long
 * line the UI truncates.
 *
 * <p>Each step is a location carrying one logical location whose {@code fullyQualifiedName} is the
 * member exactly as the chain spelled it -- {@code owner#name+descriptor}, untouched -- and whose
 * {@code kind} is {@code function}. A step that does not spell a member has no name to give, so it
 * becomes a location carrying only a message: that is how the elision a long chain renders in the
 * middle of itself, {@code ... 7 hops ...}, stays visible as a step instead of being reported as a
 * method called {@code ...}.
 *
 * <h2>The coupling</h2>
 *
 * <p>This reads a chain out of text the engine composed, so it depends on how the engine spells one:
 * the evidence line opens with a fixed prefix and joins its members with an arrow, and the two
 * constants below are the only description of that shape here. The dependency is deliberate --
 * the alternative is a second channel through the api for a fact the evidence already carries -- and it
 * is one-way, since the engine's constants are package-private and the CLI spells the prefix here
 * instead. What makes it safe is that it is pinned rather than assumed: an integration test runs a real
 * {@code --scope reachable} check over the fixture corpus and asserts the code flow's last step names
 * the consumer's referencing method, so a change to either spelling fails the build rather than quietly
 * dropping every code flow from every report.
 *
 * <p>A finding with no chain in its evidence -- every non-linkage finding, and every linkage finding
 * found at any other scope -- gets no {@code codeFlows} member at all, because an empty flow claims a
 * path was computed and found to be nothing.
 */
final class SarifCodeFlow {
    /**
     * How the engine introduces a proving chain. The one place the CLI spells it; the integration test
     * is what proves the two members still agree.
     */
    private static final String PROVING_CHAIN = "reachable via: ";

    /** How the engine joins the members of a chain. */
    private static final String STEP_SEPARATOR = " -> ";

    private static final String CODE_FLOWS = "codeFlows";
    private static final String THREAD_FLOWS = "threadFlows";
    private static final String LOCATION = "location";
    private static final String LOGICAL_LOCATIONS = "logicalLocations";
    private static final String FULLY_QUALIFIED_NAME = "fullyQualifiedName";
    private static final String KIND = "kind";
    private static final String FUNCTION = "function";
    private static final char MEMBER_SEPARATOR = '#';

    private SarifCodeFlow() {
    }

    /**
     * Writes the {@code codeFlows} member of the result object already open, when there is a chain.
     *
     * @param json the writer positioned inside a result object
     * @param evidence the finding's evidence, which may or may not prove reachability
     */
    static void append(JsonText json, List<Evidence> evidence) {
        Optional<String> chain = chain(evidence);
        if (chain.isEmpty()) {
            return;
        }
        json.name(CODE_FLOWS).beginArray();
        json.beginObject();
        json.name(THREAD_FLOWS).beginArray();
        json.beginObject();
        json.name(SarifResult.LOCATIONS).beginArray();
        for (String step : steps(chain.get())) {
            appendStep(json, step);
        }
        json.endArray();
        json.endObject();
        json.endArray();
        json.endObject();
        json.endArray();
    }

    /** The chain of the first evidence line that carries one, without its prefix. */
    private static Optional<String> chain(List<Evidence> evidence) {
        return evidence.stream()
                .map(Evidence::detail)
                .filter(detail -> detail.startsWith(PROVING_CHAIN))
                .findFirst()
                .map(detail -> detail.substring(PROVING_CHAIN.length()));
    }

    /** The chain split back into the steps the engine joined, in order. */
    private static List<String> steps(String chain) {
        List<String> steps = new ArrayList<>();
        int start = 0;
        int separator = chain.indexOf(STEP_SEPARATOR);
        while (separator >= 0) {
            steps.add(chain.substring(start, separator));
            start = separator + STEP_SEPARATOR.length();
            separator = chain.indexOf(STEP_SEPARATOR, start);
        }
        steps.add(chain.substring(start));
        return List.copyOf(steps);
    }

    private static void appendStep(JsonText json, String step) {
        json.beginObject();
        json.name(LOCATION).beginObject();
        if (step.indexOf(MEMBER_SEPARATOR) < 0) {
            appendNote(json, step);
        } else {
            appendMember(json, step);
        }
        json.endObject();
        json.endObject();
    }

    /** A step that names no member says what it is instead, which is all a reader can use. */
    private static void appendNote(JsonText json, String step) {
        json.name(SarifResult.MESSAGE).beginObject();
        json.name(SarifResult.TEXT).value(step);
        json.endObject();
    }

    private static void appendMember(JsonText json, String step) {
        json.name(LOGICAL_LOCATIONS).beginArray();
        json.beginObject();
        json.name(FULLY_QUALIFIED_NAME).value(step);
        json.name(KIND).value(FUNCTION);
        json.endObject();
        json.endArray();
    }
}
