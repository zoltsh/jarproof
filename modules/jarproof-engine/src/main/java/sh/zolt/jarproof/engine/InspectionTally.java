package sh.zolt.jarproof.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * Accumulates what an inspection has seen so far, and hands back the summary of it.
 *
 * <p>Every collection here is sorted by construction, so the summary is canonical no matter what
 * order the entries arrived in: class file versions ascend by major version, services ascend by
 * binary name, and release directories ascend by release. A service or a release directory is
 * counted once however many entries mention it, while a class file version is counted once per
 * class file, because the point of that line is how many classes sit at each level.
 */
final class InspectionTally {
    private static final String LEVEL_SEPARATOR = ":";

    private final String artifact;
    private final Map<Integer, Integer> classFilesByMajorVersion = new TreeMap<>();
    private final Set<String> services = new TreeSet<>();
    private final Set<Integer> releases = new TreeSet<>();
    private int entryCount;
    private int classCount;

    InspectionTally(String artifact) {
        this.artifact = artifact;
    }

    /**
     * Counts one entry that holds content.
     *
     * @param entryName entry name inside the artifact
     */
    void addEntry(String entryName) {
        entryCount++;
        if (InspectionLayout.isClassEntry(entryName)) {
            classCount++;
        }
        InspectionLayout.serviceName(entryName).ifPresent(services::add);
        InspectionLayout.multiReleaseVersion(entryName).ifPresent(releases::add);
    }

    /**
     * Counts the class file version one entry declares.
     *
     * @param header the opening bytes of a class entry, which need not be a class file at all
     */
    void addClassFileHeader(byte[] header) {
        InspectionLayout.majorVersion(header)
                .ifPresent(major -> classFilesByMajorVersion.merge(major, 1, Integer::sum));
    }

    /** Returns everything counted so far as one summary. */
    ArtifactSummary summary() {
        return new ArtifactSummary(
                artifact,
                entryCount,
                classCount,
                bytecodeLevels(),
                List.copyOf(services),
                List.copyOf(releases));
    }

    private List<String> bytecodeLevels() {
        return classFilesByMajorVersion.entrySet().stream()
                .map(level -> level.getKey() + LEVEL_SEPARATOR + level.getValue())
                .toList();
    }
}
