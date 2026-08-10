package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import sh.zolt.jarproof.api.Finding;

/**
 * Decides which class entry of an artifact the target runtime would actually use.
 *
 * <p>Selection happens before any check runs, because a multi-release archive that is read as if it
 * were flat reports bytecode levels and duplicate classes that no runtime ever sees. For a target
 * release, the winning entry is the one under the highest version directory that does not exceed the
 * target, and a base entry is the fallback. A versioned entry may introduce a class the base does not
 * have, which is legal and is honoured here.
 *
 * <p>Entries are keyed by their base name, so the result is both the runtime's view and a stable
 * order for reporting.
 */
final class MultiReleaseSelection {
    private static final int LOWEST_VERSIONED_RELEASE = 9;
    private static final int UNUSABLE_RELEASE = -1;
    private static final char SEGMENT_END = '/';

    private final Map<String, String> classEntries = new TreeMap<>();
    private final Map<String, Integer> selectedReleases = new TreeMap<>();
    private final Set<String> versionSegments = new TreeSet<>();
    private final List<Finding> layoutFindings = new ArrayList<>();

    private MultiReleaseSelection() {
    }

    /**
     * Selects entries for an artifact that never announced itself as multi-release.
     *
     * @param entryNames every entry name in the artifact
     * @param artifact artifact path text as the caller supplied it
     * @return base entries only, plus a diagnostic for each ignored version directory
     */
    static MultiReleaseSelection base(List<String> entryNames, String artifact) {
        MultiReleaseSelection selection = new MultiReleaseSelection();
        entryNames.forEach(selection::addUnversioned);
        selection.versionSegments.forEach(
                segment -> selection.layoutFindings.add(
                        MultiReleaseLayoutFinding.unannounced(artifact, ArchiveLayout.VERSIONS_PREFIX + segment)));
        return selection;
    }

    /**
     * Selects entries for a multi-release artifact read at one target release.
     *
     * @param entryNames every entry name in the artifact
     * @param targetRelease the Java release the classpath will run on
     * @param artifact artifact path text as the caller supplied it
     * @return the selected entries, plus a diagnostic for each unusable version directory
     */
    static MultiReleaseSelection versioned(List<String> entryNames, int targetRelease, String artifact) {
        MultiReleaseSelection selection = new MultiReleaseSelection();
        for (String entryName : entryNames) {
            selection.addVersioned(entryName, targetRelease);
        }
        selection.versionSegments.stream()
                .filter(segment -> release(segment) < LOWEST_VERSIONED_RELEASE)
                .forEach(segment -> selection.layoutFindings.add(MultiReleaseLayoutFinding.unusableDirectory(
                        artifact, ArchiveLayout.VERSIONS_PREFIX + segment)));
        return selection;
    }

    /** Returns the selected entries, keyed by base entry name and ordered by it. */
    Map<String, String> classEntries() {
        return Collections.unmodifiableMap(classEntries);
    }

    /** Returns the layout diagnostics selection produced. */
    List<Finding> layoutFindings() {
        return List.copyOf(layoutFindings);
    }

    private void addUnversioned(String entryName) {
        if (entryName.startsWith(ArchiveLayout.VERSIONS_PREFIX)) {
            versionSegments.add(versionSegment(entryName));
            return;
        }
        addBase(entryName);
    }

    private void addVersioned(String entryName, int targetRelease) {
        if (!entryName.startsWith(ArchiveLayout.VERSIONS_PREFIX)) {
            addBase(entryName);
            return;
        }
        String segment = versionSegment(entryName);
        versionSegments.add(segment);
        chooseVersioned(entryName, segment, targetRelease);
    }

    private void addBase(String entryName) {
        if (entryName.endsWith(ArchiveLayout.CLASS_SUFFIX)) {
            classEntries.putIfAbsent(entryName, entryName);
        }
    }

    private void chooseVersioned(String entryName, String segment, int targetRelease) {
        int release = release(segment);
        if (release < LOWEST_VERSIONED_RELEASE || release > targetRelease) {
            return;
        }
        int start = ArchiveLayout.VERSIONS_PREFIX.length() + segment.length() + 1;
        if (start >= entryName.length()) {
            return;
        }
        String baseEntryName = entryName.substring(start);
        if (baseEntryName.endsWith(ArchiveLayout.CLASS_SUFFIX)
                && release > selectedReleases.getOrDefault(baseEntryName, 0)) {
            selectedReleases.put(baseEntryName, release);
            classEntries.put(baseEntryName, entryName);
        }
    }

    private static String versionSegment(String entryName) {
        String remainder = entryName.substring(ArchiveLayout.VERSIONS_PREFIX.length());
        int separator = remainder.indexOf(SEGMENT_END);
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private static int release(String segment) {
        if (segment.isEmpty() || !segment.chars().allMatch(Character::isDigit)) {
            return UNUSABLE_RELEASE;
        }
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException exception) {
            return UNUSABLE_RELEASE;
        }
    }
}
