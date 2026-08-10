package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
            Set<String> records = recordNames(source);
            for (JavaMethodShape method : source.methods()) {
                String subject = method.owner() + "." + method.name();
                addIfOver(violations, subject, "lines", method.lineCount(), MAXIMUM_METHOD_LINES);
                addIfOver(violations, subject, "parameters", method.parameterCount(), parameterCeiling(method, records));
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

    /**
     * A record constructor restates the component list rather than inventing parameter plumbing, so
     * it is measured against the component ceiling that {@link #productionTypesStayFocused} already
     * enforces. Every other method keeps the tighter parameter ceiling.
     */
    private static int parameterCeiling(JavaMethodShape method, Set<String> records) {
        boolean recordConstructor = method.name().equals("<init>") && records.contains(method.owner());
        return recordConstructor ? MAXIMUM_FIELDS : MAXIMUM_PARAMETERS;
    }

    private static Set<String> recordNames(JavaSourceUnit source) {
        return source.types().stream()
                .filter(type -> type.kind().equals("RECORD"))
                .map(JavaTypeShape::qualifiedName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void addIfOver(List<String> violations, String subject, String metric, int actual, int maximum) {
        if (actual > maximum) {
            violations.add(subject + " has " + actual + " " + metric + "; maximum is " + maximum);
        }
    }
}
