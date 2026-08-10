package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every {@code ServiceLoader} fault in one provider list, and nothing invented around it.
 *
 * <p>The corpus file registers six things and gets five of them wrong, one fault per line, so a
 * single report has to separate a provider nothing declares from one that is not a subtype, from two
 * that cannot be constructed for two different reasons, from a repeat, from a line that is not a name
 * at all. Asserting the complete list rather than searching it is the point: a check that reported a
 * seventh finding here would be reporting something that does not happen at run time.
 *
 * <p>The valid half of the corpus is checked as well. A provider list that is correct has to produce
 * nothing, or the codes above would mean only that jarproof dislikes service files.
 */
final class ServiceCorpusTest {
    private static final String PROVIDER = "sh/zolt/jarproof/fixtures/badservice/";
    private static final String SERVICE = "sh/zolt/jarproof/fixtures/service/Codec";
    private static final String BAD_PROVIDERS = "bad-service-provider";
    private static final String SERVICE_API = "service-api";

    @TempDir
    Path workspace;

    @Test
    void reportsEveryFaultTheProviderListCarriesAndNothingElse() {
        CorpusCheck check = check(BAD_PROVIDERS, List.of(
                CorpusCommand.CLASSPATH, FixtureCorpus.jar(SERVICE_API).toString()));

        assertEquals(
                List.of(
                        "JP4001 " + PROVIDER + "MissingCodec",
                        "JP4002 " + PROVIDER + "UnrelatedCodec",
                        "JP4003 " + SERVICE,
                        "JP4004 " + PROVIDER + "DuplicateCodec",
                        "JP4005 " + PROVIDER + "AbstractCodec",
                        "JP4005 " + PROVIDER + "PrivateCodec"),
                check.signatures(),
                check.report());
        assertEquals(1, check.exitCode(), check.err());
    }

    @Test
    void findsNothingWrongWithTheProviderListThatIsCorrect() {
        CorpusCheck check = check(SERVICE_API, List.of());

        assertEquals(List.of(), check.findings(), check.report());
        assertEquals(0, check.exitCode(), check.err());
    }

    private CorpusCheck check(String member, List<String> extra) {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.APPLICATION, FixtureCorpus.jar(member).toString(),
                CorpusCommand.SCOPE, CorpusCommand.ALL));
        arguments.addAll(extra);
        return CorpusCheck.reporting(workspace.resolve(member + ".json"), arguments);
    }
}
