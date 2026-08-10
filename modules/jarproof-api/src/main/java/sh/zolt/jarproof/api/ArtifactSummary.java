package sh.zolt.jarproof.api;

import java.util.List;
import java.util.Objects;

/**
 * The layout facts of one artifact, read without judging any of them.
 *
 * <p>These are counts and names, not diagnostics: how many entries the artifact holds, how many of
 * them are class files, how many archives it nests, which class file versions those classes declare,
 * which services it registers providers for, and which multi-release directories it carries. Nothing
 * here is selected for a target release, so the numbers describe the artifact as it sits on disk
 * rather than the subset one runtime would use.
 *
 * <p>{@code artifact} is the path text the caller supplied, never a path resolved on this machine.
 * {@code nestedArchiveCount} counts the archive entries the artifact carries wherever they sit —
 * the layout an application fat JAR uses for its dependencies — and no nested archive is opened, so
 * a class inside one is not part of {@code classCount} while a class beside one is, however deep in
 * the layout it sits. {@code bytecodeLevels} holds one {@code major:count} pair per class file major
 * version, such as {@code 61:24}, ascending by major version. {@code declaredServices} holds service
 * binary names and {@code multiReleaseVersions} holds release numbers, both ascending. All three
 * lists are defensively copied and always unmodifiable.
 *
 * <p>This record is a stable caller-facing value, and the JSON envelope {@code jarproof inspect}
 * renders it into is a versioned contract of its own, carrying {@code inspectJsonVersion} as its
 * first member (DESIGN §3).
 */
public record ArtifactSummary(
        String artifact,
        int entryCount,
        int classCount,
        int nestedArchiveCount,
        List<String> bytecodeLevels,
        List<String> declaredServices,
        List<Integer> multiReleaseVersions) {
    public ArtifactSummary {
        Objects.requireNonNull(artifact, "A summary needs an artifact path");
        if (artifact.isBlank()) {
            throw new IllegalArgumentException("A summary must name the artifact whose facts it carries");
        }
        if (entryCount < 0 || classCount < 0 || nestedArchiveCount < 0) {
            throw new IllegalArgumentException("A summary counts what an artifact holds, so no count is negative");
        }
        Objects.requireNonNull(bytecodeLevels, "A summary needs its class file versions, even when none");
        Objects.requireNonNull(declaredServices, "A summary needs its declared services, even when none");
        Objects.requireNonNull(multiReleaseVersions, "A summary needs its release directories, even when none");
        bytecodeLevels = List.copyOf(bytecodeLevels);
        declaredServices = List.copyOf(declaredServices);
        multiReleaseVersions = List.copyOf(multiReleaseVersions);
    }
}
