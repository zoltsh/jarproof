package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

final class MethodComplexityTest {
    @Test
    void straightLineCodeStartsAtOne() {
        MethodComplexity complexity = measure("int sample() { return 1; }");

        assertEquals(1, complexity.value());
        assertEquals(0, complexity.maximumNesting());
    }

    @Test
    void branchesOperatorsAndNestingAreCounted() {
        MethodComplexity complexity = measure("""
                void sample(boolean first, boolean second) {
                    if (first && second) {
                        while (first) {
                            first = false;
                        }
                    }
                }
                """);

        assertEquals(4, complexity.value());
        assertEquals(2, complexity.maximumNesting());
    }

    @Test
    void lambdaBodiesAreMeasuredSeparatelyFromTheirOwner() {
        MethodComplexity complexity = measure("""
                void sample() {
                    Runnable action = () -> { if (true) { System.out.println("ignored"); } };
                    action.run();
                }
                """);

        assertEquals(1, complexity.value());
        assertEquals(0, complexity.maximumNesting());
    }

    private static MethodComplexity measure(String method) {
        String source = "class Sample { " + method + " }";
        JavaFileObject input = new SimpleJavaFileObject(URI.create("string:///Sample.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler()
                .getTask(null, null, null, List.of("-proc:none"), null, List.of(input));
        try {
            CompilationUnitTree unit = task.parse().iterator().next();
            MethodFinder finder = new MethodFinder();
            finder.scan(unit, null);
            return MethodComplexity.measure(finder.method.getBody());
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static final class MethodFinder extends TreeScanner<Void, Void> {
        private MethodTree method;

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (node.getName().contentEquals("sample")) {
                method = node;
            }
            return super.visitMethod(node, unused);
        }
    }
}
