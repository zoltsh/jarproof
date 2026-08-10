package sh.zolt.jarproof.architecture;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class JavaSourceParser {
    private JavaSourceParser() {
    }

    /** Parsed product code: the scope every product-code rule measures. */
    static List<JavaSourceUnit> productionSources() {
        return parse(RepositoryLayout.productionJavaFiles());
    }

    /**
     * Parsed production code of every member, fixtures included. The module boundary stays
     * repository-wide, so its fully-qualified-reference rule reads this scope rather than the
     * product-code one.
     */
    static List<JavaSourceUnit> everyProductionSource() {
        return parse(RepositoryLayout.everyProductionJavaFile());
    }

    private static List<JavaSourceUnit> parse(List<Path> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Architecture tests require a JDK, not a JRE");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            Iterable<? extends JavaFileObject> inputs = manager.getJavaFileObjectsFromPaths(files);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, manager, diagnostics, List.of("-proc:none"), null, inputs);
            List<JavaSourceUnit> sources = new ArrayList<>();
            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                Path path = Path.of(unit.getSourceFile().toUri());
                SourceScanner scanner = new SourceScanner(unit, trees.getSourcePositions());
                scanner.scan(unit, null);
                sources.add(scanner.source(path));
            }
            if (!diagnostics.getDiagnostics().isEmpty()) {
                throw new IllegalStateException("Could not parse production Java: " + diagnostics.getDiagnostics());
            }
            return List.copyOf(sources);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class SourceScanner extends TreeScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final Deque<String> owners = new ArrayDeque<>();
        private final Set<String> qualifiedReferences = new LinkedHashSet<>();
        private final List<String> stringLiterals = new ArrayList<>();
        private final List<JavaTypeShape> types = new ArrayList<>();
        private final List<JavaMethodShape> methods = new ArrayList<>();

        private SourceScanner(CompilationUnitTree unit, SourcePositions positions) {
            this.unit = unit;
            this.positions = positions;
        }

        private JavaSourceUnit source(Path path) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            List<String> imports = unit.getImports().stream()
                    .map(ImportTree::getQualifiedIdentifier)
                    .map(Object::toString)
                    .toList();
            return new JavaSourceUnit(
                    path,
                    packageName,
                    imports,
                    Set.copyOf(qualifiedReferences),
                    List.copyOf(stringLiterals),
                    List.copyOf(types),
                    List.copyOf(methods));
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void unused) {
            node.getTypeDecls().forEach(type -> scan(type, unused));
            return null;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            String owner = owners.isEmpty()
                    ? qualified(unit.getPackageName() == null ? "" : unit.getPackageName().toString(), node.getSimpleName().toString())
                    : owners.peek() + "." + node.getSimpleName();
            int fields = (int) node.getMembers().stream().filter(VariableTree.class::isInstance).count();
            int declaredMethods = (int) node.getMembers().stream().filter(MethodTree.class::isInstance).count();
            types.add(new JavaTypeShape(
                    owner,
                    node.getKind().name(),
                    Set.copyOf(node.getModifiers().getFlags()),
                    fields,
                    declaredMethods));
            owners.push(owner);
            super.visitClass(node, unused);
            owners.pop();
            return null;
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (node.getBody() != null) {
                MethodComplexity flow = MethodComplexity.measure(node.getBody());
                methods.add(new JavaMethodShape(
                        owners.peek(),
                        node.getName().toString(),
                        lineCount(node),
                        node.getParameters().size(),
                        flow.value(),
                        flow.maximumNesting(),
                        node.getParameters().stream().map(parameter -> parameter.getType().toString()).toList(),
                        Set.copyOf(node.getModifiers().getFlags())));
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void unused) {
            if (node.getValue() instanceof String value && !value.isBlank()) {
                stringLiterals.add(value);
            }
            return super.visitLiteral(node, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            String reference = node.toString();
            if (isPackageQualified(reference)) {
                qualifiedReferences.add(reference);
            }
            return super.visitMemberSelect(node, unused);
        }

        private static boolean isPackageQualified(String reference) {
            return reference.startsWith("com.")
                    || reference.startsWith("dev.")
                    || reference.startsWith("io.")
                    || reference.startsWith("jakarta.")
                    || reference.startsWith("java.")
                    || reference.startsWith("javax.")
                    || reference.startsWith("net.")
                    || reference.startsWith("org.")
                    || reference.startsWith("picocli.")
                    || reference.startsWith("sh.")
                    || reference.startsWith("software.");
        }

        private int lineCount(MethodTree method) {
            long start = positions.getStartPosition(unit, method);
            long end = positions.getEndPosition(unit, method);
            if (start < 0 || end < 0) {
                throw new IllegalStateException("Missing source position for " + owners.peek() + "." + method.getName());
            }
            long firstLine = unit.getLineMap().getLineNumber(start);
            long lastLine = unit.getLineMap().getLineNumber(end);
            return Math.toIntExact(lastLine - firstLine + 1);
        }

        private static String qualified(String packageName, String typeName) {
            return packageName.isEmpty() ? typeName : packageName + "." + typeName;
        }
    }

}
