package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DomainLiteralArchitectureTest {
    @Test
    void repeatedProductionStringsBecomeNamedVocabulary() {
        Map<String, List<String>> occurrences = new LinkedHashMap<>();
        for (JavaSourceUnit source : JavaSourceParser.productionSources()) {
            for (String literal : source.stringLiterals()) {
                if (literal.length() > 1) {
                    occurrences.computeIfAbsent(literal, ignored -> new ArrayList<>())
                            .add(RepositoryLayout.relative(source.path()));
                }
            }
        }
        List<String> duplicates = occurrences.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "`" + entry.getKey() + "` in " + entry.getValue())
                .toList();

        assertTrue(duplicates.isEmpty(),
                () -> "Replace repeated production literals with a value type, enum, or named constant:\n"
                        + String.join("\n", duplicates));
    }
}
