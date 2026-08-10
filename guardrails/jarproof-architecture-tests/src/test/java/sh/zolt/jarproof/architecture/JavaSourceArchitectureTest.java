package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class JavaSourceArchitectureTest {
    private static final int MAXIMUM_LINES = 299;
    private static final String MAIN_ROOT = "/src/main/java/";
    private static final String TEST_ROOT = "/src/test/java/";
    private static final String INTEGRATION_TEST_ROOT = "/src/integration-test/java/";
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "^(?:public\\s+|protected\\s+|private\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(?:class|interface|record|enum|@interface)\\s+\\w");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([a-zA-Z0-9_.]+);");

    @Test
    void everyJavaFileStaysBelowThreeHundredLines() {
        List<String> oversized = new ArrayList<>();
        for (Path file : RepositoryLayout.javaFiles()) {
            int lines = lines(file).size();
            if (lines > MAXIMUM_LINES) {
                oversized.add(RepositoryLayout.relative(file) + " has " + lines + " lines");
            }
        }

        assertTrue(oversized.isEmpty(), () -> "Split oversized Java files:\n" + String.join("\n", oversized));
    }

    @Test
    void everyJavaFileHasAtMostOneTopLevelType() {
        List<String> violations = new ArrayList<>();
        for (Path file : RepositoryLayout.javaFiles()) {
            long declarations = lines(file).stream().filter(line -> TOP_LEVEL_TYPE.matcher(line).find()).count();
            if (declarations > 1) {
                violations.add(RepositoryLayout.relative(file) + " has " + declarations + " top-level types");
            }
        }

        assertTrue(violations.isEmpty(), () -> "Keep one top-level type per file:\n" + String.join("\n", violations));
    }

    @Test
    void declaredPackagesMatchSourcePaths() {
        for (Path file : RepositoryLayout.javaFiles()) {
            String normalized = file.toString().replace('\\', '/');
            String marker = markerIn(normalized);
            int sourceIndex = normalized.indexOf(marker);
            assertTrue(sourceIndex >= 0, () -> RepositoryLayout.relative(file) + " is outside a Java source root");

            String expected = normalized.substring(sourceIndex + marker.length(), normalized.lastIndexOf('/'))
                    .replace('/', '.');
            Matcher declaration = PACKAGE.matcher(RepositoryLayout.text(file));
            assertTrue(declaration.find(), () -> RepositoryLayout.relative(file) + " has no package declaration");
            assertEquals(expected, declaration.group(1), RepositoryLayout.relative(file));
        }
    }

    @Test
    void productionSourcesDoNotContainTests() {
        for (String member : RepositoryLayout.workspaceMembers()) {
            for (Path file : RepositoryLayout.productionJavaFiles(member)) {
                String source = RepositoryLayout.text(file);
                assertFalse(source.contains("org.junit"), RepositoryLayout.relative(file));
                assertFalse(source.contains("@Test"), RepositoryLayout.relative(file));
                assertFalse(file.getFileName().toString().endsWith("Test.java"), RepositoryLayout.relative(file));
            }
        }
    }

    /**
     * The Java source root a file lives under. Three roots are recognised: production sources, unit
     * tests, and the integration-test root {@code zolt integration-test} compiles separately, which
     * holds the execution-assertion harness.
     */
    private static String markerIn(String normalized) {
        if (normalized.contains(MAIN_ROOT)) {
            return MAIN_ROOT;
        }
        return normalized.contains(INTEGRATION_TEST_ROOT) ? INTEGRATION_TEST_ROOT : TEST_ROOT;
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
