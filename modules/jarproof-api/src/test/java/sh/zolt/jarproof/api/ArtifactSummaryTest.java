package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ArtifactSummaryTest {
    private static final String ARTIFACT = "build/app.jar";
    private static final List<String> LEVELS = List.of("61:24", "65:3");
    private static final List<String> SERVICES = List.of("com.acme.spi.Codec");
    private static final List<Integer> VERSIONS = List.of(11, 17);

    @Test
    void keepsEverySuppliedFact() {
        ArtifactSummary summary = sample();

        assertEquals(ARTIFACT, summary.artifact());
        assertEquals(27, summary.entryCount());
        assertEquals(27, summary.classCount());
        assertEquals(2, summary.nestedArchiveCount());
        assertEquals(LEVELS, summary.bytecodeLevels());
        assertEquals(SERVICES, summary.declaredServices());
        assertEquals(VERSIONS, summary.multiReleaseVersions());
    }

    @Test
    void acceptsAnArtifactWithNothingToReport() {
        ArtifactSummary summary = new ArtifactSummary(ARTIFACT, 0, 0, 0, List.of(), List.of(), List.of());

        assertEquals(0, summary.nestedArchiveCount());
        assertEquals(List.of(), summary.bytecodeLevels());
        assertEquals(List.of(), summary.declaredServices());
        assertEquals(List.of(), summary.multiReleaseVersions());
    }

    @Test
    void copiesTheClassFileVersionsDefensively() {
        List<String> levels = new ArrayList<>(LEVELS);
        ArtifactSummary summary = new ArtifactSummary(ARTIFACT, 27, 27, 2, levels, SERVICES, VERSIONS);

        levels.add("52:1");

        assertEquals(LEVELS, summary.bytecodeLevels());
        assertThrows(UnsupportedOperationException.class, () -> summary.bytecodeLevels().add("52:1"));
    }

    @Test
    void copiesTheDeclaredServicesDefensively() {
        List<String> services = new ArrayList<>(SERVICES);
        ArtifactSummary summary = new ArtifactSummary(ARTIFACT, 27, 27, 2, LEVELS, services, VERSIONS);

        services.add("com.acme.spi.Late");

        assertEquals(SERVICES, summary.declaredServices());
        assertThrows(
                UnsupportedOperationException.class, () -> summary.declaredServices().add("com.acme.spi.Late"));
    }

    @Test
    void copiesTheReleaseDirectoriesDefensively() {
        List<Integer> versions = new ArrayList<>(VERSIONS);
        ArtifactSummary summary = new ArtifactSummary(ARTIFACT, 27, 27, 2, LEVELS, SERVICES, versions);

        versions.add(21);

        assertEquals(VERSIONS, summary.multiReleaseVersions());
        assertThrows(UnsupportedOperationException.class, () -> summary.multiReleaseVersions().add(21));
    }

    @Test
    void rejectsAnArtifactPathThatNamesNothing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactSummary(" ", 0, 0, 0, List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsACountOfLessThanNothing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactSummary(ARTIFACT, -1, 0, 0, List.of(), List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactSummary(ARTIFACT, 0, -1, 0, List.of(), List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactSummary(ARTIFACT, 0, 0, -1, List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsAnyMissingComponent() {
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactSummary(null, 0, 0, 0, List.of(), List.of(), List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactSummary(ARTIFACT, 0, 0, 0, null, List.of(), List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactSummary(ARTIFACT, 0, 0, 0, List.of(), null, List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactSummary(ARTIFACT, 0, 0, 0, List.of(), List.of(), null));
    }

    @Test
    void comparesByEveryComponent() {
        ArtifactSummary summary = sample();

        assertEquals(summary, sample());
        assertEquals(summary.hashCode(), sample().hashCode());
        assertNotEquals(summary, new ArtifactSummary("other.jar", 27, 27, 2, LEVELS, SERVICES, VERSIONS));
        assertNotEquals(summary, new ArtifactSummary(ARTIFACT, 28, 27, 2, LEVELS, SERVICES, VERSIONS));
        assertNotEquals(summary, new ArtifactSummary(ARTIFACT, 27, 26, 2, LEVELS, SERVICES, VERSIONS));
        assertNotEquals(summary, new ArtifactSummary(ARTIFACT, 27, 27, 1, LEVELS, SERVICES, VERSIONS));
        assertNotEquals(summary, new ArtifactSummary(ARTIFACT, 27, 27, 2, List.of(), SERVICES, VERSIONS));
        assertNotEquals(summary, new ArtifactSummary(ARTIFACT, 27, 27, 2, LEVELS, List.of(), VERSIONS));
        assertNotEquals(summary, new ArtifactSummary(ARTIFACT, 27, 27, 2, LEVELS, SERVICES, List.of()));
    }

    private static ArtifactSummary sample() {
        return new ArtifactSummary(ARTIFACT, 27, 27, 2, LEVELS, SERVICES, VERSIONS);
    }
}
