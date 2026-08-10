package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.cli.CorpusCheck.Reported;
import sh.zolt.jarproof.cli.CorpusProcess.Outcome;

/**
 * The claim jarproof is sold on, tested by execution: the predicted error is the error.
 *
 * <p>Every case runs the same consumer twice on a real JVM — once against the version it was
 * compiled against, once against the version that broke it — and then asks jarproof about the
 * broken combination. The first run proves the consumer is correct code rather than something that
 * fails everywhere. The second run makes the JVM name the throwable. The check then has to predict
 * that exact name, and the clean combination has to stay silent, so neither a missed finding nor an
 * invented one can pass.
 *
 * <p>The throwable is asserted from the first line the JVM printed, which is the throwable it
 * actually raised rather than one wrapped inside it. That matters most for the method-reference
 * case, where a resolution failure inside a bootstrap method could plausibly surface as a
 * {@code BootstrapMethodError} and does not.
 */
final class LinkageExecutionTest {
    private static final String THROWN = "Exception in thread \"main\" java.lang.";
    private static final String ERROR = "error";
    private static final String CONSUMER = "-consumer";
    private static final String API_V1 = "-api-v1";
    private static final String API_V2 = "-api-v2";
    private static final String FIXTURE_PACKAGE = "sh.zolt.jarproof.fixtures.";
    private static final String SUBJECT_PACKAGE = "sh/zolt/jarproof/fixtures/";
    private static final String REPORT_SUFFIX = ".report.json";

    @TempDir
    Path workspace;

    @Test
    void predictsTheErrorARemovedMethodCauses() {
        proves("missing-method", "missingmethod.consumer.OrderReport", "JP1003", "NoSuchMethodError",
                "missingmethod/OrderPolicy#describe(Ljava/lang/String;I)Ljava/lang/String;");
    }

    @Test
    void predictsTheErrorADeletedClassCauses() {
        proves("missing-class", "missingclass.consumer.ReceiptRun", "JP1001", "NoClassDefFoundError",
                "missingclass/LegacyReceipt");
    }

    @Test
    void predictsTheErrorARemovedFieldCauses() {
        proves("missing-field", "missingfield.consumer.RetryRun", "JP1002", "NoSuchFieldError",
                "missingfield/RetryBudget#maximumAttemptsI");
    }

    @Test
    void predictsTheErrorAStaticBecomingAnInstanceMethodCauses() {
        proves("static-instance-flip", "staticflip.consumer.ClockRun", "JP1004", "IncompatibleClassChangeError",
                "staticflip/ClockSource#label()Ljava/lang/String;");
    }

    @Test
    void predictsTheErrorAClassBecomingAnInterfaceCauses() {
        proves("class-to-interface", "classkind.consumer.RenderRun", "JP1005", "IncompatibleClassChangeError",
                "classkind/Renderer#render()Ljava/lang/String;");
    }

    @Test
    void predictsTheErrorANarrowedClassCauses() {
        proves("inaccessible", "inaccessible.consumer.VaultRun", "JP1006", "IllegalAccessError",
                "inaccessible/SecretVault");
    }

    @Test
    void predictsTheErrorARemovedMethodReferenceTargetCauses() {
        proves("lambda-target", "lambdatarget.consumer.GreetingRun", "JP1003", "NoSuchMethodError",
                "lambdatarget/ApiGreetings#formal()Ljava/lang/String;");
    }

    /**
     * Asserts both halves of one broken pair.
     *
     * @param pair the fixture family, without its {@code -consumer} or {@code -api-vN} suffix
     * @param mainClass the consumer's main class, without the fixture package prefix
     * @param code the diagnostic code the check must report
     * @param throwable the simple name of the throwable the JVM must raise
     * @param subject the finding subject, without the fixture package prefix
     */
    private void proves(String pair, String mainClass, String code, String throwable, String subject) {
        Path consumer = FixtureCorpus.jar(pair + CONSUMER);
        Path repaired = FixtureCorpus.jar(pair + API_V1);
        Path broken = FixtureCorpus.jar(pair + API_V2);

        raises(consumer, broken, mainClass, throwable);
        runs(consumer, repaired, mainClass);
        reports(consumer, broken, new Reported(
                code, ERROR, throwable, FixtureCorpus.relativePath(consumer), SUBJECT_PACKAGE + subject));
        staysSilent(consumer, repaired);
    }

    /** The JVM raises the throwable itself, not something wrapping it. */
    private void raises(Path consumer, Path api, String mainClass, String throwable) {
        Outcome outcome = launch(consumer, api, mainClass);

        assertNotEquals(0, outcome.exitCode(), outcome.out());
        assertTrue(outcome.err().startsWith(THROWN + throwable + ":"), outcome.err());
    }

    /** The same consumer against the version it was compiled against is a working program. */
    private void runs(Path consumer, Path api, String mainClass) {
        Outcome outcome = launch(consumer, api, mainClass);

        assertEquals(0, outcome.exitCode(), outcome.err());
    }

    private void reports(Path consumer, Path api, Reported expected) {
        CorpusCheck check = check(consumer, api);

        assertEquals(List.of(expected), check.findings(), check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    private void staysSilent(Path consumer, Path api) {
        CorpusCheck check = check(consumer, api);

        assertEquals(List.of(), check.findings(), check.report());
        assertEquals(0, check.exitCode(), check.err());
    }

    private CorpusCheck check(Path consumer, Path api) {
        return CorpusCheck.reporting(
                workspace.resolve(api.getFileName() + REPORT_SUFFIX),
                List.of(
                        CorpusCommand.APPLICATION, consumer.toString(),
                        CorpusCommand.CLASSPATH, api.toString()));
    }

    private Outcome launch(Path consumer, Path api, String mainClass) {
        return CorpusProcess.launch(
                workspace,
                consumer + File.pathSeparator + api,
                FIXTURE_PACKAGE + mainClass);
    }
}
