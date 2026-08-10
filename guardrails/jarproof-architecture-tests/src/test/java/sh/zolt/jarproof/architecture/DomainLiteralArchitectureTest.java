package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DomainLiteralArchitectureTest {
    /**
     * Literal vocabulary deduplicates per member, not per repository: the workspace layers keep
     * implementation types package-private, so two members cannot share a constant, and identical
     * spellings in different members usually name different domains — {@code version} is both a
     * Maven {@code pom.properties} key in the engine and a JSON envelope key in the CLI. Within
     * one member a repeated literal is still a missing constant and still fails.
     */
    @Test
    void repeatedProductionStringsBecomeNamedVocabulary() {
        List<JavaSourceUnit> sources = JavaSourceParser.productionSources();
        List<String> duplicates = new ArrayList<>();
        for (String member : RepositoryLayout.coreMembers().stream().sorted().toList()) {
            duplicates.addAll(duplicatesWithin(member, sources));
        }

        assertTrue(duplicates.isEmpty(),
                () -> "Replace repeated production literals with a value type, enum, or named constant:\n"
                        + String.join("\n", duplicates));
    }

    private static List<String> duplicatesWithin(String member, List<JavaSourceUnit> sources) {
        Map<String, List<String>> occurrences = new LinkedHashMap<>();
        for (JavaSourceUnit source : sources) {
            String relative = RepositoryLayout.relative(source.path());
            if (!relative.startsWith(member + "/")) {
                continue;
            }
            for (String literal : source.stringLiterals()) {
                if (literal.length() > 1) {
                    occurrences.computeIfAbsent(literal, ignored -> new ArrayList<>()).add(relative);
                }
            }
        }
        return occurrences.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "`" + entry.getKey() + "` in " + entry.getValue())
                .toList();
    }
}
