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
    private static final String SOURCE_FILE = "Preconditions.java";
    private static final Optional<String> SOURCE = Optional.of(SOURCE_FILE);
    private static final Optional<Integer> LINE = Optional.of(122);

    @Test
    void locatesAWholeArtifact() {
        ArtifactLocation location = ArtifactLocation.ofArtifact(ARTIFACT);

        assertEquals(ARTIFACT, location.artifact());
        assertTrue(location.classEntry().isEmpty());
        assertTrue(location.sourceFile().isEmpty());
        assertTrue(location.line().isEmpty());
    }

    @Test
    void locatesOneClassEntryInsideAnArtifact() {
        ArtifactLocation location = ArtifactLocation.ofClassEntry(ARTIFACT, CLASS_ENTRY);

        assertEquals(ARTIFACT, location.artifact());
        assertEquals(Optional.of(CLASS_ENTRY), location.classEntry());
        assertTrue(location.sourceFile().isEmpty());
        assertTrue(location.line().isEmpty());
    }

    @Test
    void locatesTheSourceLineAClassFileDeclares() {
        ArtifactLocation location = ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, LINE);

        assertEquals(Optional.of(CLASS_ENTRY), location.classEntry());
        assertEquals(SOURCE, location.sourceFile());
        assertEquals(LINE, location.line());
    }

    @Test
    void acceptsASourceFileWithoutALine() {
        ArtifactLocation location =
                ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, Optional.empty());

        assertEquals(SOURCE, location.sourceFile());
        assertTrue(location.line().isEmpty());
    }

    @Test
    void acceptsAClassCompiledWithoutDebugInformation() {
        ArtifactLocation location =
                ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, Optional.empty(), Optional.empty());

        assertEquals(ArtifactLocation.ofClassEntry(ARTIFACT, CLASS_ENTRY), location);
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
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactLocation(ARTIFACT, null, Optional.empty(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> ArtifactLocation.ofClassEntry(ARTIFACT, null));
    }

    @Test
    void rejectsABlankClassEntry() {
        assertThrows(IllegalArgumentException.class, () -> ArtifactLocation.ofClassEntry(ARTIFACT, ""));
        assertThrows(IllegalArgumentException.class, () -> ArtifactLocation.ofClassEntry(ARTIFACT, "\t"));
    }

    @Test
    void rejectsAMissingSourceDecision() {
        assertThrows(
                NullPointerException.class,
                () -> new ArtifactLocation(ARTIFACT, Optional.of(CLASS_ENTRY), null, Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, null));
    }

    @Test
    void rejectsABlankSourceFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, Optional.of(""), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, Optional.of(" "), Optional.empty()));
    }

    @Test
    void rejectsALineBeforeTheFirstOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, Optional.of(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, Optional.of(-1)));
    }

    @Test
    void rejectsALineWithoutTheClassEntryItIsALineOf() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactLocation(ARTIFACT, Optional.empty(), SOURCE, LINE));
    }

    @Test
    void distinguishesWholeArtifactsFromClassEntriesAndSourceLines() {
        ArtifactLocation whole = ArtifactLocation.ofArtifact(ARTIFACT);
        ArtifactLocation entry = ArtifactLocation.ofClassEntry(ARTIFACT, CLASS_ENTRY);
        ArtifactLocation line = ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, LINE);

        assertEquals(whole, ArtifactLocation.ofArtifact(ARTIFACT));
        assertEquals(whole.hashCode(), ArtifactLocation.ofArtifact(ARTIFACT).hashCode());
        assertEquals(line, ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, LINE));
        assertNotEquals(whole, entry);
        assertNotEquals(entry, line);
        assertNotEquals(line, ArtifactLocation.ofSource(ARTIFACT, CLASS_ENTRY, SOURCE, Optional.of(123)));
        assertNotEquals(entry, ArtifactLocation.ofClassEntry("app.jar", CLASS_ENTRY));
    }
}
