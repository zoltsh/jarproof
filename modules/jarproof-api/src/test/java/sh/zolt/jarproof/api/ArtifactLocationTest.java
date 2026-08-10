package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ArtifactLocationTest {
    private static final String ARTIFACT = "lib/guava-18.0.jar";
    private static final String CLASS_ENTRY = "com/google/common/base/Preconditions.class";

    @Test
    void locatesAWholeArtifact() {
        ArtifactLocation location = ArtifactLocation.ofArtifact(ARTIFACT);

        assertEquals(ARTIFACT, location.artifact());
        assertTrue(location.classEntry().isEmpty());
    }

    @Test
    void locatesOneClassEntryInsideAnArtifact() {
        ArtifactLocation location = ArtifactLocation.ofClassEntry(ARTIFACT, CLASS_ENTRY);

        assertEquals(ARTIFACT, location.artifact());
        assertEquals(Optional.of(CLASS_ENTRY), location.classEntry());
    }

    @Test
    void keepsTheCallerSuppliedPathVerbatim() {
        ArtifactLocation location = ArtifactLocation.ofArtifact("./lib/../lib/guava-18.0.jar");

        assertEquals("./lib/../lib/guava-18.0.jar", location.artifact());
    }

    @Test
    void rejectsAMissingArtifactPath() {
        assertThrows(NullPointerException.class, () -> ArtifactLocation.ofArtifact(null));
        assertThrows(NullPointerException.class, () -> ArtifactLocation.ofClassEntry(null, CLASS_ENTRY));
    }

    @Test
    void rejectsABlankArtifactPath() {
        assertThrows(IllegalArgumentException.class, () -> ArtifactLocation.ofArtifact(""));
        assertThrows(IllegalArgumentException.class, () -> ArtifactLocation.ofArtifact("   "));
    }

    @Test
    void rejectsAMissingClassEntryDecision() {
        assertThrows(NullPointerException.class, () -> new ArtifactLocation(ARTIFACT, null));
        assertThrows(NullPointerException.class, () -> ArtifactLocation.ofClassEntry(ARTIFACT, null));
    }

    @Test
    void rejectsABlankClassEntry() {
        assertThrows(IllegalArgumentException.class, () -> ArtifactLocation.ofClassEntry(ARTIFACT, ""));
        assertThrows(IllegalArgumentException.class, () -> ArtifactLocation.ofClassEntry(ARTIFACT, "\t"));
    }

    @Test
    void distinguishesWholeArtifactsFromClassEntries() {
        ArtifactLocation whole = ArtifactLocation.ofArtifact(ARTIFACT);
        ArtifactLocation entry = ArtifactLocation.ofClassEntry(ARTIFACT, CLASS_ENTRY);

        assertEquals(whole, ArtifactLocation.ofArtifact(ARTIFACT));
        assertEquals(whole.hashCode(), ArtifactLocation.ofArtifact(ARTIFACT).hashCode());
        assertNotEquals(whole, entry);
        assertNotEquals(entry, ArtifactLocation.ofClassEntry("app.jar", CLASS_ENTRY));
    }
}
