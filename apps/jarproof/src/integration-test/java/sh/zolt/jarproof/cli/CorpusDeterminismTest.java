package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every report the corpus produces, twice, compared byte for byte.
 *
 * <p>A release gate that answers differently on two runs of the same inputs cannot be trusted with
 * either answer, and the ways to lose determinism are all quiet: an unordered map, a set iteration, a
 * hash in a message, a path that is absolute on one machine. Rooting every report at the workspace
 * removes the one legitimate difference, so anything left is a defect.
 *
 * <p>The sweep is the whole corpus rather than a sample, broken and clean combinations alike, because
 * an empty report has an ordering contract too. Its report count is asserted first, so a comparison
 * of two empty sweeps can never be mistaken for a comparison of two identical ones.
 */
final class CorpusDeterminismTest {
    private static final List<String> PAIRS = List.of(
            "missing-method",
            "missing-class",
            "missing-field",
            "static-instance-flip",
            "class-to-interface",
            "inaccessible",
            "lambda-target");
    private static final String CONSUMER = "-consumer";
    private static final String API_V1 = "-api-v1";
    private static final String API_V2 = "-api-v2";
    private static final String REPORT_SUFFIX = ".json";
    private static final String ENVELOPE = "\"jarproofJsonVersion\"";
    private static final int SWEPT_REPORTS = 21;

    @TempDir
    Path workspace;

    @Test
    void rendersTheSameBytesForTheWholeCorpusEveryRun() {
        byte[] first = sweep(workspace.resolve("first"));
        byte[] second = sweep(workspace.resolve("second"));

        String swept = new String(first, StandardCharsets.UTF_8);
        assertEquals(SWEPT_REPORTS, swept.split(ENVELOPE, -1).length - 1, swept);
        assertArrayEquals(first, second, swept);
    }

    private byte[] sweep(Path reports) {
        createDirectory(reports);
        StringBuilder swept = new StringBuilder();
        for (String pair : PAIRS) {
            swept.append(verify(reports, pair + API_V2, pair + CONSUMER, List.of(pair + API_V2)));
            swept.append(verify(reports, pair + API_V1, pair + CONSUMER, List.of(pair + API_V1)));
        }
        swept.append(verify(reports, "nestmates", "nestmates", List.of()));
        swept.append(verify(reports, "method-handle-poly", "method-handle-poly", List.of()));
        swept.append(verify(reports, "services", "bad-service-provider", List.of("service-api")));
        swept.append(verify(reports, "service-api", "service-api", List.of()));
        swept.append(verify(
                reports, "duplicates", "nestmates", List.of("duplicate-class-a", "duplicate-class-b")));
        swept.append(verify(reports, "split", "nestmates", List.of("split-package-a", "split-package-b")));
        swept.append(verify(reports, "newer-bytecode", "newer-bytecode", List.of()));
        return swept.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String verify(Path reports, String name, String application, List<String> classpath) {
        List<String> arguments = new ArrayList<>(List.of(
                CorpusCommand.APPLICATION, FixtureCorpus.jar(application).toString(),
                CorpusCommand.SCOPE, CorpusCommand.ALL));
        for (String member : classpath) {
            arguments.addAll(List.of(CorpusCommand.CLASSPATH, FixtureCorpus.jar(member).toString()));
        }
        return CorpusCheck.reporting(reports.resolve(name + REPORT_SUFFIX), arguments).report();
    }

    private static void createDirectory(Path reports) {
        try {
            Files.createDirectories(reports);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
