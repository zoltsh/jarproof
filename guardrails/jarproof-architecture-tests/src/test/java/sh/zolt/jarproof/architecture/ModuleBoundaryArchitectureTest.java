package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String FIXTURES = "fixtures/";
    /**
     * Fixture corpus members, enumerated so no fixture can join the workspace without a reviewed
     * boundary rule. Corpus code is exempt from product-code policy (see
     * {@link RepositoryLayout#coreMembers()}) but never from the module boundary: a fixture reaches
     * the JDK and its own fixture packages, nothing else — least of all the api, engine, or CLI it
     * is analysis input for.
     */
    private static final List<String> FIXTURE_MEMBERS = List.of(
            "bad-service-provider",
            "class-to-interface-api-v1",
            "class-to-interface-api-v2",
            "class-to-interface-consumer",
            "duplicate-class-a",
            "duplicate-class-b",
            "inaccessible-api-v1",
            "inaccessible-api-v2",
            "inaccessible-consumer",
            "lambda-target-api-v1",
            "lambda-target-api-v2",
            "lambda-target-consumer",
            "method-handle-poly",
            "missing-class-api-v1",
            "missing-class-api-v2",
            "missing-class-consumer",
            "missing-field-api-v1",
            "missing-field-api-v2",
            "missing-field-consumer",
            "missing-method-api-v1",
            "missing-method-api-v2",
            "missing-method-consumer",
            "nestmates",
            "newer-bytecode",
            "service-api",
            "split-package-a",
            "split-package-b",
            "static-instance-flip-api-v1",
            "static-instance-flip-api-v2",
            "static-instance-flip-consumer");
    /**
     * The only workspace edge a fixture may declare: a consumer compiles against the {@code v1} side
     * of its pair so the {@code v2} side can break it at runtime, and a service-provider fixture
     * compiles against the service interface it claims to provide.
     */
    private static final Map<String, String> FIXTURE_DEPENDENCIES = Map.ofEntries(
            Map.entry("bad-service-provider", "service-api"),
            Map.entry("class-to-interface-consumer", "class-to-interface-api-v1"),
            Map.entry("inaccessible-consumer", "inaccessible-api-v1"),
            Map.entry("lambda-target-consumer", "lambda-target-api-v1"),
            Map.entry("missing-class-consumer", "missing-class-api-v1"),
            Map.entry("missing-field-consumer", "missing-field-api-v1"),
            Map.entry("missing-method-consumer", "missing-method-api-v1"),
            Map.entry("static-instance-flip-consumer", "static-instance-flip-api-v1"));
    private static final Set<String> FIXTURE_IMPORTS = Set.of("java.", "sh.zolt.jarproof.fixtures.");
    private static final Map<String, Set<String>> ALLOWED_WORKSPACE_DEPENDENCIES = allowedWorkspaceDependencies();
    private static final Map<String, Set<String>> ALLOWED_IMPORT_PREFIXES = allowedImportPrefixes();
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
        for (JavaSourceUnit source : JavaSourceParser.everyProductionSource()) {
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

    private static Map<String, Set<String>> allowedWorkspaceDependencies() {
        Map<String, Set<String>> rules = new LinkedHashMap<>();
        rules.put(API, Set.of());
        rules.put(ENGINE, Set.of(API));
        rules.put(CLI, Set.of(API, ENGINE));
        rules.put(ARCHITECTURE, Set.of());
        for (String fixture : FIXTURE_MEMBERS) {
            String dependency = FIXTURE_DEPENDENCIES.get(fixture);
            rules.put(FIXTURES + fixture, dependency == null ? Set.of() : Set.of(FIXTURES + dependency));
        }
        return Map.copyOf(rules);
    }

    private static Map<String, Set<String>> allowedImportPrefixes() {
        Map<String, Set<String>> rules = new LinkedHashMap<>();
        rules.put(API, Set.of("java.", "javax.", "sh.zolt.jarproof.api."));
        rules.put(ENGINE, Set.of("java.", "javax.", "org.objectweb.asm.", "sh.zolt.jarproof.api.", "sh.zolt.jarproof.engine."));
        rules.put(CLI, Set.of("java.", "javax.", "picocli.", "sh.zolt.jarproof.api.", "sh.zolt.jarproof.engine.", "sh.zolt.jarproof.cli."));
        rules.put(ARCHITECTURE, Set.of("java.", "javax.", "sh.zolt.jarproof.architecture."));
        FIXTURE_MEMBERS.forEach(fixture -> rules.put(FIXTURES + fixture, FIXTURE_IMPORTS));
        return Map.copyOf(rules);
    }

    private static String memberFor(Path source) {
        String relative = RepositoryLayout.relative(source);
        return ALLOWED_IMPORT_PREFIXES.keySet().stream()
                .filter(member -> relative.startsWith(member + "/"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No member rule for " + relative));
    }
}
