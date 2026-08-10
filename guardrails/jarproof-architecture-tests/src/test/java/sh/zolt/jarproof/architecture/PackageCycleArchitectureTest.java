package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PackageCycleArchitectureTest {
    @Test
    void productionPackagesRemainAcyclic() {
        List<JavaSourceUnit> sources = JavaSourceParser.productionSources();
        List<String> packages = sources.stream()
                .map(JavaSourceUnit::packageName)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        packages.forEach(packageName -> graph.put(packageName, new LinkedHashSet<>()));
        for (JavaSourceUnit source : sources) {
            java.util.stream.Stream.concat(source.imports().stream(), source.qualifiedReferences().stream())
                    .map(reference -> packageFor(reference, packages))
                    .flatMap(java.util.Optional::stream)
                    .filter(target -> !target.equals(source.packageName()))
                    .forEach(target -> graph.get(source.packageName()).add(target));
        }

        List<String> cycles = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String packageName : packages) {
            visit(packageName, graph, new ArrayList<>(), visited, cycles);
        }
        assertTrue(cycles.isEmpty(), () -> "Production package dependency cycles:\n" + String.join("\n", cycles));
    }

    private static java.util.Optional<String> packageFor(String reference, List<String> packages) {
        return packages.stream()
                .filter(packageName -> reference.equals(packageName) || reference.startsWith(packageName + "."))
                .findFirst();
    }

    private static void visit(
            String current,
            Map<String, Set<String>> graph,
            List<String> path,
            Set<String> visited,
            List<String> cycles) {
        int cycleStart = path.indexOf(current);
        if (cycleStart >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(current);
            cycles.add(String.join(" -> ", cycle));
            return;
        }
        if (!visited.add(current)) {
            return;
        }
        path.add(current);
        for (String dependency : graph.getOrDefault(current, Set.of())) {
            visit(dependency, graph, path, visited, cycles);
        }
        path.removeLast();
    }
}
