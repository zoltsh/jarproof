package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/**
 * The chain a reachable-scope finding carries as evidence: what it says, and what it says when the path
 * is too long to print.
 *
 * <p>Every assertion here reads the finding rather than the graph. The graph's own reconstruction is
 * proved by {@link ReachabilityTest}; what this measures is the line a user actually sees, because a
 * chain that is correct in the engine and absent from the report explains nothing to anybody.
 */
final class ReachableChainTest {
    private static final String MAIN_CLASS = "com/acme/app/Entry";
    private static final String ALPHA = "com/acme/lib/Alpha";
    private static final String STEP = "com/acme/deep/Step";
    private static final String ABSENT = "com/acme/gone/Absent";
    private static final String SECOND_ABSENT = "com/acme/gone/Other";
    private static final String PROOF = "reachable via: ";
    private static final String ARROW = " -> ";
    private static final String ENTRY_METHOD = MAIN_CLASS + "#" + ReachableFixture.MAIN;

    @TempDir
    Path workspace;

    /**
     * A break in first-party code is reached by nothing above it, and that is the answer rather than a
     * missing one: the referencing method is entry surface, so the chain is that method alone. "Your own
     * code, directly" is why it runs, and a reader who sees one node has been told exactly that.
     *
     * <p>The same corpus at the two quieter scopes settles the other half of the contract in the same
     * breath. Their evidence is byte for byte identical to each other, and the reachable run's evidence
     * is exactly theirs with the chain appended — so the chain is the only addition, it lands last after
     * the facts about what failed to resolve, and neither quieter mode gained a line it never had.
     */
    @Test
    void provesAnApplicationBreakWithTheReferencingMethodAlone() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.call(ABSENT, ReachableFixture.WORK))));

        List<Finding> proven = scoped(application, Scope.REACHABLE);
        List<Finding> firstParty = scoped(application, Scope.APPLICATION);
        List<Finding> everyOrigin = scoped(application, Scope.ALL);

        assertEquals(List.of("JP1001"), EngineFixture.codes(proven));
        assertEquals(EngineFixture.evidence(firstParty.get(0)), EngineFixture.evidence(everyOrigin.get(0)));
        assertEquals(
                appended(EngineFixture.evidence(firstParty.get(0)), PROOF + ENTRY_METHOD),
                EngineFixture.evidence(proven.get(0)));
    }

    /**
     * Two breaks written in one method are two findings, and each carries the chain itself. Computing
     * the path once per method is an implementation nicety; a finding that made the reader look the
     * chain up in a sibling finding would not be carrying evidence at all.
     */
    @Test
    void givesEveryFindingSharingAReferencingMethodItsOwnChain() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.call(ALPHA, ReachableFixture.WORK))));
        Path library = jar("pair", LinkageFixture.classes(ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.body(
                        ReachableFixture.WORK,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK),
                        ReachableFixture.call(SECOND_ABSENT, ReachableFixture.WORK)))));

        List<Finding> findings = reachable(application, library);
        String expected = PROOF + ENTRY_METHOD + ARROW + ALPHA + "#" + ReachableFixture.WORK;

        assertEquals(List.of("JP1001", "JP1001"), EngineFixture.codes(findings));
        assertEquals(List.of(expected, expected), findings.stream().map(ReachableChainTest::proof).toList());
    }

    /**
     * The ceiling renders whole. Twelve steps is a path a reader can still follow, and shortening it
     * would drop hops for no benefit, so the boundary is asserted from the long side rather than assumed.
     */
    @Test
    void rendersEveryHopOfAChainAtTheCeiling() {
        List<Finding> findings = reachable(entryInto(11), jar("whole", ReachableFixture.linearChain(
                STEP, 11, ABSENT)));

        assertEquals(List.of("JP1001"), EngineFixture.codes(findings));
        assertEquals(
                PROOF + chainOf(
                        ENTRY_METHOD,
                        step(0), step(1), step(2), step(3), step(4),
                        step(5), step(6), step(7), step(8), step(9), step(10)),
                proof(findings.get(0)));
    }

    /**
     * One hop past the ceiling, the middle goes. Both ends survive because both ends are what a reader
     * needs — the first-party method that starts the path, and the method the break is written in — and
     * the note between them counts what was dropped instead of pretending nothing was.
     */
    @Test
    void shortensTheMiddleOfAChainOneHopPastTheCeiling() {
        List<Finding> findings = reachable(entryInto(12), jar("shortened", ReachableFixture.linearChain(
                STEP, 12, ABSENT)));

        assertEquals(
                PROOF + chainOf(
                        ENTRY_METHOD, step(0), step(1), step(2), step(3),
                        "... 3 hops ...",
                        step(7), step(8), step(9), step(10), step(11)),
                proof(findings.get(0)));
    }

    /** A chain far past the ceiling keeps the same two shoulders, and the count grows with the path. */
    @Test
    void countsEveryHopItLeavesOutOfADeepChain() {
        List<Finding> findings = reachable(entryInto(20), jar("deep", ReachableFixture.linearChain(
                STEP, 20, ABSENT)));

        assertEquals(
                PROOF + chainOf(
                        ENTRY_METHOD, step(0), step(1), step(2), step(3),
                        "... 11 hops ...",
                        step(15), step(16), step(17), step(18), step(19)),
                proof(findings.get(0)));
    }

    /**
     * The forest is byte-stable. Caller pointers are written once, at first discovery, from a worklist
     * drained in ascending node order, so the order the classes were written into the archive cannot
     * reach the evidence — asserted over the whole evidence list rather than the chain alone, because
     * a chain that is stable inside an unstable report is not a stable report.
     */
    @Test
    void rendersTheSameEvidenceWhateverOrderTheClassesWereWrittenIn() {
        Map<String, byte[]> chain = ReachableFixture.linearChain(STEP, 14, ABSENT);

        List<Finding> ordered = reachable(entryInto(14), jar("ordered", chain));
        List<Finding> flipped = reachable(entryInto(14), jar("flipped", reversed(chain)));

        assertEquals(EngineFixture.evidence(ordered.get(0)), EngineFixture.evidence(flipped.get(0)));
        assertEquals(1, flipped.size(), flipped.toString());
    }

    private List<Finding> reachable(Path application, Path library) {
        return LinkageFixture.check(List.of(application), List.of(library), Scope.REACHABLE);
    }

    private List<Finding> scoped(Path application, Scope scope) {
        return LinkageFixture.check(List.of(application), List.of(), scope);
    }

    private static List<String> appended(List<String> evidence, String line) {
        List<String> whole = new ArrayList<>(evidence);
        whole.add(line);
        return List.copyOf(whole);
    }

    /** An application whose {@code main} calls the first step of a chain of the given length. */
    private Path entryInto(int steps) {
        return app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.call(STEP + 0, ReachableFixture.WORK))), steps);
    }

    private Path app(byte[] classFile) {
        return app(classFile, 0);
    }

    /** The archive name carries the chain length, because one workspace holds every case at once. */
    private Path app(byte[] classFile, int steps) {
        return EngineFixture.jar(
                workspace, "app-" + steps + ".jar", LinkageFixture.classes(MAIN_CLASS, classFile));
    }

    private Path jar(String name, Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "lib/" + name + ".jar", entries);
    }

    private static String step(int index) {
        return STEP + index + "#" + ReachableFixture.WORK;
    }

    private static String chainOf(String... steps) {
        return String.join(ARROW, steps);
    }

    /** The one evidence line that names the proving chain, or a failure naming what was carried. */
    private static String proof(Finding finding) {
        return EngineFixture.evidence(finding).stream()
                .filter(line -> line.startsWith(PROOF))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no chain in " + EngineFixture.evidence(finding)));
    }

    private static Map<String, byte[]> reversed(Map<String, byte[]> entries) {
        List<Map.Entry<String, byte[]>> pairs = new ArrayList<>(entries.entrySet());
        Collections.reverse(pairs);
        Map<String, byte[]> flipped = new LinkedHashMap<>();
        pairs.forEach(pair -> flipped.put(pair.getKey(), pair.getValue()));
        return flipped;
    }
}
