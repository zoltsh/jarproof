package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.VerificationRequest;

final class DeterministicOutputTest {
    private static final String DUPLICATE = "com/acme/dup/Duplicate";
    private static final String ROOT_MARKER = "ROOT";

    @TempDir
    Path workspace;

    @Test
    void producesTheSameReportWhateverOrderTheInputsWereBuiltIn() {
        List<String> ordered = report(workspace.resolve("ordered"), false);
        List<String> shuffled = report(workspace.resolve("shuffled"), true);

        assertEquals(ordered, shuffled);
        assertTrue(ordered.size() > 6, ordered.toString());
    }

    @Test
    void producesTheSameReportEveryRun() {
        Path root = workspace.resolve("repeated");
        VerificationRequest request = messyClasspath(root, false);

        assertEquals(
                render(root, EngineFixture.verify(request)),
                render(root, EngineFixture.verify(request)));
    }

    @Test
    void ordersFindingsByArtifactThenCodeThenSubject() {
        Path root = workspace.resolve("sorted");
        List<Finding> findings = EngineFixture.verify(messyClasspath(root, false));

        List<Finding> resorted = new ArrayList<>(findings);
        Collections.shuffle(resorted, new java.util.Random(7));
        resorted.sort(FindingOrder.CANONICAL);

        assertEquals(render(root, findings), render(root, resorted));
    }

    private List<String> report(Path root, boolean shuffle) {
        return render(root, EngineFixture.verify(messyClasspath(root, shuffle)));
    }

    private static VerificationRequest messyClasspath(Path root, boolean shuffle) {
        Path application = EngineFixture.jar(root, "app.jar", order(shuffle, applicationEntries()));
        Path sealed = EngineFixture.jar(root, "lib/sealed.jar", order(shuffle, sealedEntries()));
        Path intruder = EngineFixture.jar(root, "lib/intruder.jar",
                EngineFixture.entries("com/acme/sealed/Intruder.class",
                        EngineFixture.classFile("com/acme/sealed/Intruder")));
        Path chained = EngineFixture.jar(root, "lib/chained.jar", order(shuffle, chainedEntries()));
        EngineFixture.jar(root, "wild/alpha.jar",
                EngineFixture.entries(DUPLICATE + ".class", EngineFixture.classFile(DUPLICATE)));
        EngineFixture.jar(root, "wild/beta.jar",
                EngineFixture.entries(DUPLICATE + ".class", EngineFixture.classFileWithField(DUPLICATE, "extra")));
        return EngineFixture.request(
                List.of(application),
                List.of(sealed, intruder, chained, root.resolve("wild").resolve("*")),
                17);
    }

    private static Map<String, byte[]> applicationEntries() {
        Map<String, byte[]> entries = EngineFixture.entries();
        entries.put("com/acme/App.class", EngineFixture.classFile("com/acme/App"));
        entries.put("com/acme/split/First.class", EngineFixture.classFile("com/acme/split/First", Opcodes.V1_8));
        entries.put("com/acme/broken/Broken.class", "not bytecode".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/versions/9/com/acme/App.class", EngineFixture.classFile("com/acme/App"));
        return entries;
    }

    private static Map<String, byte[]> sealedEntries() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.SEALED, "true");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "widgets");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0.0");
        Map<String, byte[]> entries = EngineFixture.entries();
        entries.put("com/acme/sealed/Owner.class", EngineFixture.classFile("com/acme/sealed/Owner"));
        entries.put("com/acme/split/Second.class", EngineFixture.classFile("com/acme/split/Second"));
        return EngineFixture.withManifest(entries, manifest);
    }

    private static Map<String, byte[]> chainedEntries() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "absent.jar");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "widgets");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "2.0.0");
        return EngineFixture.withManifest(
                EngineFixture.entries("com/acme/chained/Chained.class",
                        EngineFixture.classFile("com/acme/chained/Chained")),
                manifest);
    }

    private static Map<String, byte[]> order(boolean shuffle, Map<String, byte[]> source) {
        List<Map.Entry<String, byte[]>> pairs = new ArrayList<>(source.entrySet());
        if (shuffle) {
            Collections.reverse(pairs);
        }
        Map<String, byte[]> ordered = new LinkedHashMap<>();
        pairs.forEach(pair -> ordered.put(pair.getKey(), pair.getValue()));
        return ordered;
    }

    private static List<String> render(Path root, List<Finding> findings) {
        return findings.stream().map(finding -> String.join("|",
                        finding.code().value(),
                        finding.severity().name(),
                        finding.predictedError().value(),
                        relative(root, finding.artifact().artifact()),
                        relative(root, finding.artifact().classEntry().orElse(ROOT_MARKER)),
                        relative(root, finding.subject()),
                        finding.summary(),
                        finding.evidence().stream().map(Evidence::detail).map(detail -> relative(root, detail))
                                .collect(Collectors.joining(";")),
                        finding.remediation().stream().map(Remediation::action)
                                .collect(Collectors.joining(";"))))
                .toList();
    }

    private static String relative(Path root, String text) {
        return text.replace(root.toString(), ROOT_MARKER);
    }
}
