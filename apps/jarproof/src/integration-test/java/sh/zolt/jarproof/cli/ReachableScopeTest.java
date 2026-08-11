package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CorpusCheck.Reported;

/**
 * What {@code --scope reachable} costs and what it buys, measured on real artifacts.
 *
 * <p>Two claims are under test and they pull in opposite directions. A break the shipped consumer
 * really executes has to survive the reachability filter, or the mode has quietly stopped reporting
 * true positives; and a healthy real-world closure has to come out completely silent, or the mode is
 * no quieter than {@code --scope all} and the method-level analysis bought nothing. Both are asserted
 * against the corpus the other integration tests use, because a precision claim about synthesised
 * bytecode is not a precision claim.
 *
 * <p>The precision bar is the second one, and it is the reason this mode exists. The closure checked
 * here holds Netty's optional Log4J2, Log4J, and SLF4J logging back ends and the JUnit console's
 * Kotlin support, none of them present on the classpath and none of them executed by an application
 * that calls Guava, Jackson, and Netty buffers. At {@code --scope all} those are the warnings DESIGN
 * section 4 describes. Here they have to be gone. If one appears the analysis became too loose, and
 * the failure message names every survivor with the class and method its reference is written in —
 * the last hop of the chain that reached it, and where reading the bytecode starts.
 */
final class ReachableScopeTest {
    private static final String REACHABLE = "reachable";
    private static final String CLOSURE_REPORT = "reachable-corpus.json";
    private static final String LINKAGE_PREFIX = "JP1";
    private static final String CALL_SITE = "referenced from ";
    private static final String PROVING_CHAIN = "reachable via: ";
    private static final String FIXTURE_SUBJECT = "sh/zolt/jarproof/fixtures/";
    private static final String CONSUMER_MAIN =
            "missingmethod/consumer/OrderReport#main([Ljava/lang/String;)V";
    private static final String CONSUMER = "-consumer";
    private static final String BROKEN_API = "-api-v2";
    private static final String LOUD = "The healthy corpus is not silent at --scope reachable, so the"
            + " call graph reached library bytecode this application never runs:\n";
    private static final String QUIET = "A break the consumer's own main executes disappeared at"
            + " --scope reachable, so the filter is dropping true positives:\n";

    @TempDir
    static Path workspace;

    private static Path application;
    private static List<Path> libraries;

    @BeforeAll
    static void buildTheApplicationOnce() {
        application = HealthyApplication.jar(workspace);
        libraries = LibraryClosure.jars();
    }

    /** The precision bar: nothing in a healthy closure is both provably executable and broken. */
    @Test
    void reportsNoLinkageFindingAtAllForAHealthyClosure() {
        CorpusCheck check = CorpusCheck.reporting(workspace.resolve(CLOSURE_REPORT), againstTheClosure());

        assertEquals(List.of(), linkage(check), () -> LOUD + callSites(check.report()));
        assertEquals(0, check.exitCode(), check.err());
    }

    /**
     * Every broken fixture pair still reports its exact diagnostic, because every one of those
     * consumers calls the removed member from its own {@code main}, which the entry surface presumes
     * live.
     */
    @Test
    void stillReportsEveryBreakTheFixtureConsumersExecute() {
        proves("missing-method", "JP1003",
                "missingmethod/OrderPolicy#describe(Ljava/lang/String;I)Ljava/lang/String;");
        proves("missing-class", "JP1001", "missingclass/LegacyReceipt");
        proves("missing-field", "JP1002", "missingfield/RetryBudget#maximumAttemptsI");
        proves("static-instance-flip", "JP1004", "staticflip/ClockSource#label()Ljava/lang/String;");
        proves("class-to-interface", "JP1005", "classkind/Renderer#render()Ljava/lang/String;");
        proves("inaccessible", "JP1006", "inaccessible/SecretVault");
    }

    /**
     * The mode explains itself on a real artifact pair. The finding it keeps carries the chain that
     * proves its referencing method executes, read back out of the machine report rather than out of the
     * engine, so the evidence a consumer of the JSON sees is the thing under test.
     *
     * <p>The chain is one node long here, and that is the point rather than a shortfall: the removed
     * method is called from the consumer's own {@code main}, the entry surface presumes first-party code
     * live, and "your own code, directly" is the whole proof. A mode that printed nothing in that case
     * would be silent exactly where the answer is simplest.
     */
    @Test
    void carriesTheChainThatProvesASurvivingFindingExecutes() {
        CorpusCheck check = reachable("missing-method-chain", List.of(
                CorpusCommand.APPLICATION, FixtureCorpus.jar("missing-method" + CONSUMER).toString(),
                CorpusCommand.CLASSPATH, FixtureCorpus.jar("missing-method" + BROKEN_API).toString()));

        assertEquals(
                List.of(PROVING_CHAIN + FIXTURE_SUBJECT + CONSUMER_MAIN),
                chains(check.report()),
                check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    /**
     * The chain reaches a code-scanning UI as a code flow, and this is the contract that keeps it
     * reaching one.
     *
     * <p>The SARIF writer reconstructs the path by reading the engine's own evidence text -- the
     * {@code reachable via: } prefix and the arrow between members -- and it cannot import those
     * spellings, because the engine keeps them package-private. So the two members agree only by
     * convention, and a convention nothing executes is a convention that drifts: rename the prefix and
     * every report would keep validating while silently carrying no code flow at all. This asserts the
     * whole path instead, from a real {@code --scope reachable} run of the broken pair to the last step
     * of the rendered flow, which has to name the consumer's own {@code main} -- the method the removed
     * call is written in, and the method the chain proved executes.
     */
    @Test
    void carriesTheProvingChainIntoSarifAsACodeFlow() throws IOException {
        Path report = workspace.resolve("missing-method-" + REACHABLE + ".sarif");
        int status = sarif(report, List.of(
                CorpusCommand.APPLICATION, FixtureCorpus.jar("missing-method" + CONSUMER).toString(),
                CorpusCommand.CLASSPATH, FixtureCorpus.jar("missing-method" + BROKEN_API).toString()));
        String document = Files.readString(report);

        assertEquals(FIXTURE_SUBJECT + CONSUMER_MAIN, lastStepOfTheOnlyCodeFlow(document), document);
        assertEquals(1, status, document);
    }

    /**
     * A method reference names its target through a bootstrap argument rather than a call instruction,
     * and the lambda body it names counts as reachable once the capture site is. That
     * over-approximation is deliberate, and asserting it here is what stops a later tightening from
     * losing it quietly.
     */
    @Test
    void reachesAMethodReferenceTargetThroughItsBootstrapArgument() {
        proves("lambda-target", "JP1003", "lambdatarget/ApiGreetings#formal()Ljava/lang/String;");
    }

    /** The fixtures that exist to prove a check stays quiet stay quiet, filter or no filter. */
    @Test
    void staysSilentAboutCorrectBytecodeAVerifierMightMisread() {
        staysSilent("nestmates");
        staysSilent("method-handle-poly");
    }

    private void proves(String pair, String code, String subject) {
        Path consumer = FixtureCorpus.jar(pair + CONSUMER);
        CorpusCheck check = reachable(pair, List.of(
                CorpusCommand.APPLICATION, consumer.toString(),
                CorpusCommand.CLASSPATH, FixtureCorpus.jar(pair + BROKEN_API).toString()));

        assertEquals(
                List.of(code + " " + FIXTURE_SUBJECT + subject),
                check.signatures(),
                () -> QUIET + check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    private void staysSilent(String member) {
        CorpusCheck check = reachable(
                member, List.of(CorpusCommand.APPLICATION, FixtureCorpus.jar(member).toString()));

        assertEquals(List.of(), check.findings(), check.report());
        assertEquals(0, check.exitCode(), check.err());
    }

    /**
     * Runs one reachable-scope check that writes SARIF, through the process entry point the harness
     * uses everywhere else. The shared runner reports JSON, and a format is not a detail a report
     * assertion can substitute for afterwards.
     */
    private static int sarif(Path report, List<String> subject) {
        List<String> line = new ArrayList<>(List.of(CorpusCommand.CHECK));
        line.addAll(subject);
        line.addAll(List.of(
                CorpusCommand.SCOPE, REACHABLE,
                CorpusCommand.TARGET_JAVA, CorpusCommand.JAVA_17,
                CorpusCommand.FORMAT, CanonicalName.of(ReportFormat.SARIF),
                CorpusCommand.OUTPUT, report.toString(),
                CorpusCommand.PATH_ROOT, FixtureCorpus.workspaceRoot().toString()));
        StringWriter written = new StringWriter();
        PrintWriter stream = new PrintWriter(written, true);
        return Main.execute(stream, stream, line.toArray(new String[0]));
    }

    /**
     * The member the last step of the only code flow names, read with the CLI's own scanner so the
     * assertion measures the parsed document rather than a substring of it.
     */
    private static String lastStepOfTheOnlyCodeFlow(String document) {
        Map<?, ?> run = object(array(object(JsonScanner.parse(document)).get("runs")).get(0));
        Map<?, ?> result = object(array(run.get("results")).get(0));
        Map<?, ?> flow = object(array(object(array(result.get("codeFlows")).get(0)).get("threadFlows")).get(0));
        List<?> steps = array(flow.get("locations"));
        Map<?, ?> last = object(object(steps.get(steps.size() - 1)).get("location"));
        return (String) object(array(last.get("logicalLocations")).get(0)).get("fullyQualifiedName");
    }

    private CorpusCheck reachable(String name, List<String> subject) {
        List<String> arguments = new ArrayList<>(subject);
        arguments.addAll(List.of(CorpusCommand.SCOPE, REACHABLE));
        return CorpusCheck.reporting(workspace.resolve(name + "-" + REACHABLE + ".json"), arguments);
    }

    /** Only the linkage family answers to scope, so only the linkage family is the bar. */
    private static List<String> linkage(CorpusCheck check) {
        return check.findings().stream()
                .filter(finding -> finding.code().startsWith(LINKAGE_PREFIX))
                .map(Reported::signature)
                .toList();
    }

    /**
     * Names each finding the report carries with the method its reference is written in, read back with
     * the CLI's own scanner so the message cannot drift from what was actually written.
     */
    private static String callSites(String report) {
        StringJoiner lines = new StringJoiner("\n", "", "\n");
        for (Object element : array(object(JsonScanner.parse(report)).get("findings"))) {
            Map<?, ?> finding = object(element);
            if (((String) finding.get("code")).startsWith(LINKAGE_PREFIX)) {
                lines.add(finding.get("code")
                        + " " + finding.get("subject")
                        + " in " + object(finding.get(FindingJson.ARTIFACT)).get("classEntry")
                        + ", " + evidenceLine(finding));
            }
        }
        return lines.toString();
    }

    /**
     * Every proving chain the report carries, in report order, read with the CLI's own scanner so an
     * assertion measures parsed values rather than a substring of formatted text.
     */
    private static List<String> chains(String report) {
        List<String> proven = new ArrayList<>();
        for (Object element : array(object(JsonScanner.parse(report)).get("findings"))) {
            for (Object detail : array(object(element).get("evidence"))) {
                if (((String) detail).startsWith(PROVING_CHAIN)) {
                    proven.add((String) detail);
                }
            }
        }
        return List.copyOf(proven);
    }

    private static String evidenceLine(Map<?, ?> finding) {
        for (Object detail : array(finding.get("evidence"))) {
            if (((String) detail).startsWith(CALL_SITE)) {
                return (String) detail;
            }
        }
        return CALL_SITE;
    }

    private static List<String> againstTheClosure() {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.APPLICATION, application.toString(),
                CorpusCommand.SCOPE, REACHABLE));
        for (Path library : libraries) {
            arguments.addAll(List.of(CorpusCommand.CLASSPATH, library.toString()));
        }
        return arguments;
    }

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> array(Object value) {
        return (List<?>) value;
    }
}
