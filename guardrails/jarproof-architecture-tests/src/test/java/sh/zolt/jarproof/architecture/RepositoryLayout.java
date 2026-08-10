package sh.zolt.jarproof.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class RepositoryLayout {
    private static final Pattern MEMBER_BLOCK = Pattern.compile("members\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern WORKSPACE_DEPENDENCY = Pattern.compile("workspace\\s*=\\s*\"([^\"]+)\"");

    private RepositoryLayout() {
    }

    static Path root() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            Path manifest = candidate.resolve("zolt.toml");
            if (Files.isRegularFile(manifest) && text(manifest).contains("name = \"jarproof\"")) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the Jarproof workspace root");
    }

    static List<Path> javaFiles() {
        return files(root(), path -> path.toString().endsWith(".java"));
    }

    static List<Path> productionJavaFiles(String member) {
        Path source = root().resolve(member).resolve("src/main/java");
        if (!Files.isDirectory(source)) {
            return List.of();
        }
        return files(source, path -> path.toString().endsWith(".java"));
    }

    static List<Path> productionJavaFiles() {
        return workspaceMembers().stream()
                .flatMap(member -> productionJavaFiles(member).stream())
                .sorted()
                .toList();
    }

    static Set<String> workspaceMembers() {
        String manifest = text(root().resolve("zolt.toml"));
        Matcher block = MEMBER_BLOCK.matcher(manifest);
        if (!block.find()) {
            throw new IllegalStateException("Workspace members are not declared");
        }
        return quotedValues(block.group(1));
    }

    static Set<String> workspaceDependencies(String member) {
        return matches(dependencySection(member), WORKSPACE_DEPENDENCY);
    }

    static Set<String> dependencyCoordinates(String member) {
        return matches(dependencySection(member), Pattern.compile("(?m)^\"([^\"]+)\"\\s*="));
    }

    private static String dependencySection(String member) {
        String manifest = text(root().resolve(member).resolve("zolt.toml"));
        int start = manifest.indexOf("[dependencies]");
        if (start < 0) {
            return "";
        }
        int end = manifest.indexOf("\n[", start + 1);
        return end < 0 ? manifest.substring(start) : manifest.substring(start, end);
    }

    static String text(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static String relative(Path path) {
        return root().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static List<Path> files(Path start, java.util.function.Predicate<Path> predicate) {
        try (Stream<Path> paths = Files.walk(start)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(predicate)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Set<String> quotedValues(String source) {
        return matches(source, QUOTED_VALUE);
    }

    private static Set<String> matches(String source, Pattern pattern) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return Set.copyOf(values);
    }
}
