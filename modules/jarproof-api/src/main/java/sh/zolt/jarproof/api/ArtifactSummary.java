package sh.zolt.jarproof.api;

import java.util.List;
import java.util.Objects;

/**
 * The layout facts of one artifact, read without judging any of them.
 *
 * <p>These are counts and names, not diagnostics: how many entries the artifact holds, how many of
 * them are class files, which class file versions those declare, which services it registers
 * providers for, and which multi-release directories it carries. Nothing here is selected for a
 * target release, so the numbers describe the artifact as it sits on disk rather than the subset
 * one runtime would use.
 *
 * <p>{@code artifact} is the path text the caller supplied, never a path resolved on this machine.
 * {@code bytecodeLevels} holds one {@code major:count} pair per class file major version, such as
 * {@code 61:24}, ascending by major version. {@code declaredServices} holds service binary names
 * and {@code multiReleaseVersions} holds release numbers, both ascending. All three lists are
 * defensively copied and always unmodifiable.
 *
 * <p>This record is a stable caller-facing value. How {@code jarproof inspect} renders it as JSON
 * is explicitly unstable in 0.1.x, unlike the {@code check} JSON envelope, which is a versioned
 * contract (DESIGN §3).
 */
public record ArtifactSummary(
        String artifact,
        int entryCount,
        int classCount,
        List<String> bytecodeLevels,
        List<String> declaredServices,
        List<Integer> multiReleaseVersions) {
    public ArtifactSummary {
        Objects.requireNonNull(artifact, "A summary needs an artifact path");
        if (artifact.isBlank()) {
            throw new IllegalArgumentException("A summary must name the artifact whose facts it carries");
        }
        Objects.requireNonNull(bytecodeLevels, "A summary needs its class file versions, even when none");
        Objects.requireNonNull(declaredServices, "A summary needs its declared services, even when none");
        Objects.requireNonNull(multiReleaseVersions, "A summary needs its release directories, even when none");
        bytecodeLevels = List.copyOf(bytecodeLevels);
        declaredServices = List.copyOf(declaredServices);
        multiReleaseVersions = List.copyOf(multiReleaseVersions);
    }
}
