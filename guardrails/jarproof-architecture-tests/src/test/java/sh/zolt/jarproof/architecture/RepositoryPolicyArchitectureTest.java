package sh.zolt.jarproof.architecture;

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
            return paths.filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/.zolt/"))
                    .filter(path -> !path.toString().contains("/.claude/"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
