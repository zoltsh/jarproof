package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class RepositoryPolicyArchitectureTest {
    private static final Set<String> FORBIDDEN_BUILD_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "gradlew", "gradlew.bat", "mvnw", "mvnw.cmd");
    private static final Pattern ACTION = Pattern.compile("(?m)^\\s*-?\\s*uses:\\s*([^\\s#]+)");
    private static final Pattern IMMUTABLE_REF = Pattern.compile("[^@]+@[0-9a-f]{40}");

    @Test
    void zoltIsTheOnlyBuildTool() {
        for (Path file : repositoryFiles()) {
            assertFalse(FORBIDDEN_BUILD_FILES.contains(file.getFileName().toString()), RepositoryLayout.relative(file));
        }
    }

    @Test
    void repositoryContainsNoNestedGitMetadata() {
        for (Path path : repositoryPaths()) {
            if (path.equals(RepositoryLayout.root().resolve(".git"))) {
                continue;
            }
            assertFalse(path.getFileName().toString().equals(".git"), RepositoryLayout.relative(path));
            assertFalse(path.getFileName().toString().equals(".gitmodules"), RepositoryLayout.relative(path));
        }
    }

    @Test
    void requiredToolVersionIsMachineReadableAndImmutable() {
        String manifest = RepositoryLayout.text(RepositoryLayout.root().resolve("zolt.toml"));
        assertTrue(manifest.contains("[toolchain.zolt]"));
        assertTrue(manifest.matches("(?s).*\\[toolchain\\.zolt].*version = \"[^\"]+\".*"));
        assertFalse(manifest.matches("(?s).*\\[toolchain\\.zolt].*version = \"(?:latest|.*SNAPSHOT)\".*"));
    }

    /**
     * The version the product reports must be the version the build declares: {@code --version},
     * the JSON envelope, and the SARIF driver all read {@code ProductIdentity.VERSION}, while
     * artifact names come from the member manifest — releasing with the two out of step ships
     * binaries that misreport themselves with a green test suite.
     */
    @Test
    void reportedVersionMatchesTheDeclaredMemberVersion() {
        String declared = RepositoryLayout.text(RepositoryLayout.root().resolve("apps/jarproof/zolt.toml"));
        Matcher version = Pattern.compile("(?m)^version = \"([^\"]+)\"").matcher(declared);
        assertTrue(version.find(), "apps/jarproof/zolt.toml declares no version");
        String identity = RepositoryLayout.text(RepositoryLayout.root()
                .resolve("apps/jarproof/src/main/java/sh/zolt/jarproof/cli/ProductIdentity.java"));
        assertTrue(identity.contains("\"" + version.group(1) + "\""),
                "ProductIdentity.VERSION does not carry the declared version " + version.group(1));
    }

    /**
     * The workspace coverage aggregate cannot include fixture members: they carry no tests, and
     * the corpus deliberately declares the same class in two members, which the coverage analyzer
     * rejects outright. The gate therefore names the measured members explicitly — and this rule
     * keeps that list honest, so a new core member cannot silently escape the coverage floors.
     */
    @Test
    void coverageGateMeasuresExactlyTheCoreMembers() {
        String gate = RepositoryLayout.text(RepositoryLayout.root().resolve("scripts/check"));
        Matcher coverage = Pattern.compile("(?m)^zolt coverage --workspace --members (\\S+)$").matcher(gate);
        assertTrue(coverage.find(), "scripts/check must run coverage over an explicit member list");
        assertEquals(RepositoryLayout.coreMembers(), Set.of(coverage.group(1).split(",")));
    }

    @Test
    void testGateRunsExactlyTheCoreMembers() {
        String gate = RepositoryLayout.text(RepositoryLayout.root().resolve("scripts/check"));
        Matcher test = Pattern.compile("(?m)^zolt test --workspace --members (\\S+)$").matcher(gate);
        assertTrue(test.find(), "scripts/check must test an explicit member list");
        assertEquals(RepositoryLayout.coreMembers(), Set.of(test.group(1).split(",")));
    }

    @Test
    void repositoryGateRunsSmokeTestsWithAPinnedSmoqueRelease() {
        String gate = RepositoryLayout.text(RepositoryLayout.root().resolve("scripts/check"));
        assertTrue(gate.contains("JARPROOF_SMOKE_PREBUILT=1 scripts/smoke"));
        String smoke = RepositoryLayout.text(RepositoryLayout.root().resolve("scripts/smoke"));
        assertTrue(smoke.matches("(?s).*SMOQUE_PACKAGE=.*smoque@[0-9]+\\.[0-9]+\\.[0-9]+.*"));
        assertFalse(smoke.contains("smoque@latest"));
    }

    @Test
    void dependencyAutomationUsesTheZoltActions() {
        String submission = RepositoryLayout.text(
                RepositoryLayout.root().resolve(".github/workflows/dependency-submission.yml"));
        assertTrue(submission.contains("zoltsh/submit-dependencies@"));
        assertTrue(submission.contains("workspace: \"true\""));
        String updates = RepositoryLayout.text(
                RepositoryLayout.root().resolve(".github/workflows/dependency-updates.yml"));
        assertTrue(updates.contains("zoltsh/update-dependencies@"));
        assertTrue(updates.contains("dry-run: \"false\""));
        assertTrue(updates.contains("actions: write"));
        assertTrue(updates.contains("gh workflow run ci.yml"));
    }

    @Test
    void junitVersionIsOwnedByTheWorkspaceBom() {
        String rootManifest = RepositoryLayout.text(RepositoryLayout.root().resolve("zolt.toml"));
        assertTrue(rootManifest.matches(
                "(?s).*\\[platforms].*\"org\\.junit:junit-bom\" = \"[0-9]+\\.[0-9]+\\.[0-9]+\".*"));
        Pattern literalJUnit = Pattern.compile("(?m)^\"org\\.junit\\.[^\"]+\"\\s*=\\s*\"");
        for (String member : RepositoryLayout.workspaceMembers()) {
            String manifest = RepositoryLayout.text(RepositoryLayout.root().resolve(member).resolve("zolt.toml"));
            assertFalse(manifest.contains("junit-platform-console-standalone"), member);
            assertFalse(literalJUnit.matcher(manifest).find(), member);
            if (RepositoryLayout.coreMembers().contains(member)) {
                assertTrue(manifest.contains("\"org.junit.jupiter:junit-jupiter\" = {}"), member);
                assertTrue(manifest.contains("\"org.junit.platform:junit-platform-console\" = {}"), member);
            }
        }
    }

    @Test
    void everyExternalActionUsesAFullCommitSha() {
        Path workflows = RepositoryLayout.root().resolve(".github/workflows");
        assertTrue(Files.isDirectory(workflows), "Missing GitHub workflows");
        for (Path workflow : filesUnder(workflows).stream().filter(Files::isRegularFile).toList()) {
            Matcher actions = ACTION.matcher(RepositoryLayout.text(workflow));
            while (actions.find()) {
                String action = actions.group(1);
                if (!action.startsWith("./")) {
                    assertTrue(IMMUTABLE_REF.matcher(action).matches(), RepositoryLayout.relative(workflow) + ": " + action);
                }
            }
        }
    }

    private static List<Path> repositoryFiles() {
        return repositoryPaths().stream().filter(Files::isRegularFile).toList();
    }

    private static List<Path> repositoryPaths() {
        return filesUnder(RepositoryLayout.root());
    }

    private static List<Path> filesUnder(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> insideRepository(root.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean insideRepository(Path relative) {
        String inside = "/" + relative.toString().replace('\\', '/');
        return !inside.contains("/target/") && !inside.contains("/.zolt/") && !inside.contains("/.claude/");
    }
}
