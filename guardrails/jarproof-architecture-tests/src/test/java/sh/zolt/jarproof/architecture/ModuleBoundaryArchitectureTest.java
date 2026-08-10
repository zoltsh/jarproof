package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ModuleBoundaryArchitectureTest {
    private static final String API = "modules/jarproof-api";
    private static final String ENGINE = "modules/jarproof-engine";
    private static final String CLI = "apps/jarproof";
    private static final String ARCHITECTURE = "guardrails/jarproof-architecture-tests";
    private static final Map<String, Set<String>> ALLOWED_WORKSPACE_DEPENDENCIES = Map.of(
            API, Set.of(),
            ENGINE, Set.of(API),
            CLI, Set.of(API, ENGINE),
            ARCHITECTURE, Set.of());
    private static final Map<String, Set<String>> ALLOWED_IMPORT_PREFIXES = Map.of(
            API, Set.of("java.", "javax.", "sh.zolt.jarproof.api."),
            ENGINE, Set.of("java.", "javax.", "org.objectweb.asm.", "sh.zolt.jarproof.api.", "sh.zolt.jarproof.engine."),
            CLI, Set.of("java.", "javax.", "picocli.", "sh.zolt.jarproof.api.", "sh.zolt.jarproof.engine.", "sh.zolt.jarproof.cli."),
            ARCHITECTURE, Set.of("java.", "javax.", "sh.zolt.jarproof.architecture."));
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");

    @Test
    void everyWorkspaceMemberHasAnExplicitBoundaryRule() {
        assertEquals(ALLOWED_WORKSPACE_DEPENDENCIES.keySet(), RepositoryLayout.workspaceMembers());
    }

    @Test
    void workspaceDependenciesPointOnlyInward() {
        for (Map.Entry<String, Set<String>> rule : ALLOWED_WORKSPACE_DEPENDENCIES.entrySet()) {
            assertEquals(rule.getValue(), RepositoryLayout.workspaceDependencies(rule.getKey()), rule.getKey());
        }
    }

    @Test
    void productionImportsStayInsideTheirLayer() {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> rule : ALLOWED_IMPORT_PREFIXES.entrySet()) {
            for (Path source : RepositoryLayout.productionJavaFiles(rule.getKey())) {
                Matcher imports = IMPORT.matcher(RepositoryLayout.text(source));
                while (imports.find()) {
                    String imported = imports.group(1);
                    boolean allowed = rule.getValue().stream().anyMatch(imported::startsWith);
                    if (!allowed) {
                        violations.add(RepositoryLayout.relative(source) + " imports " + imported);
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Illegal production imports:\n" + String.join("\n", violations));
    }

    @Test
    void fullyQualifiedReferencesCannotBypassImportRules() {
        List<String> violations = new ArrayList<>();
        for (JavaSourceUnit source : JavaSourceParser.productionSources()) {
            String member = memberFor(source.path());
            Set<String> allowed = ALLOWED_IMPORT_PREFIXES.get(member);
            for (String reference : source.qualifiedReferences()) {
                if (allowed.stream().noneMatch(reference::startsWith)) {
                    violations.add(RepositoryLayout.relative(source.path()) + " references " + reference);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "Illegal fully qualified production references:\n" + String.join("\n", violations));
    }

    private static String memberFor(Path source) {
        String relative = RepositoryLayout.relative(source);
        return ALLOWED_IMPORT_PREFIXES.keySet().stream()
                .filter(member -> relative.startsWith(member + "/"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No member rule for " + relative));
    }
}
