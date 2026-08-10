package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MethodDesignArchitectureTest {
    private static final int MAXIMUM_METHOD_LINES = 40;
    private static final int MAXIMUM_PARAMETERS = 5;
    private static final int MAXIMUM_COMPLEXITY = 8;
    private static final int MAXIMUM_NESTING = 3;
    private static final int MAXIMUM_FIELDS = 12;
    private static final int MAXIMUM_METHODS_PER_TYPE = 15;

    @Test
    void productionMethodsStayReadable() {
        List<String> violations = new ArrayList<>();
        for (JavaSourceUnit source : JavaSourceParser.productionSources()) {
            for (JavaMethodShape method : source.methods()) {
                String subject = method.owner() + "." + method.name();
                addIfOver(violations, subject, "lines", method.lineCount(), MAXIMUM_METHOD_LINES);
                addIfOver(violations, subject, "parameters", method.parameterCount(), MAXIMUM_PARAMETERS);
                addIfOver(violations, subject, "complexity", method.complexity(), MAXIMUM_COMPLEXITY);
                addIfOver(violations, subject, "nesting", method.maximumNesting(), MAXIMUM_NESTING);
            }
        }

        assertTrue(violations.isEmpty(), () -> "Split or simplify production methods:\n" + String.join("\n", violations));
    }

    @Test
    void productionTypesStayFocused() {
        List<String> violations = new ArrayList<>();
        for (JavaSourceUnit source : JavaSourceParser.productionSources()) {
            for (JavaTypeShape type : source.types()) {
                addIfOver(violations, type.qualifiedName(), "fields", type.fieldCount(), MAXIMUM_FIELDS);
                addIfOver(violations, type.qualifiedName(), "methods", type.methodCount(), MAXIMUM_METHODS_PER_TYPE);
            }
        }

        assertTrue(violations.isEmpty(), () -> "Split unfocused production types:\n" + String.join("\n", violations));
    }

    private static void addIfOver(List<String> violations, String subject, String metric, int actual, int maximum) {
        if (actual > maximum) {
            violations.add(subject + " has " + actual + " " + metric + "; maximum is " + maximum);
        }
    }
}
