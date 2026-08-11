package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArtifactCatalogTest {
    private static final String SHARED = "com/acme/shared/Shared";

    @TempDir
    Path workspace;

    @Test
    void answersWithTheDeclarationTheRuntimeWouldReach() {
        Path winner = jar("lib/winner.jar", SHARED);
        Path loser = jar("lib/loser.jar", SHARED);
        ArtifactCatalog catalog = read(List.of(winner), List.of(loser));

        ClassDeclaration reached = catalog.winner(SHARED).orElseThrow();

        assertEquals(winner.toString(), reached.artifactPath());
        assertEquals(SHARED + ArchiveLayout.CLASS_SUFFIX, reached.entryName());
        assertEquals(SHARED, reached.internalName());
        assertEquals(Optional.empty(), reached.wildcardSource());
        assertEquals(2, catalog.declarations().get(SHARED).size());
    }

    @Test
    void answersWithNothingForAClassNoArtifactDeclares() {
        ArtifactCatalog catalog = read(List.of(jar("app.jar", "com/acme/App")), List.of());

        assertEquals(Optional.empty(), catalog.winner("com/acme/Absent"));
    }

    @Test
    void groupsContributingArtifactsByPackageInClasspathOrder() {
        Path first = jar("lib/first.jar", "com/acme/shared/First");
        Path second = jar("lib/second.jar", "com/acme/shared/Second");
        ArtifactCatalog catalog = read(List.of(first), List.of(second));

        assertEquals(
                Set.of(first.toString(), second.toString()),
                Set.copyOf(catalog.artifactsByPackage().get("com/acme/shared")));
        assertEquals(first.toString(), catalog.artifactsByPackage().get("com/acme/shared").iterator().next());
    }

    @Test
    void keepsTheClasspathItWasBuiltFrom() {
        Path application = jar("app.jar", "com/acme/App");
        ArtifactCatalog catalog = read(List.of(application), List.of());

        assertEquals(1, catalog.classpath().entries().size());
        assertEquals(ClasspathOrigin.APPLICATION, catalog.classpath().entries().get(0).origin());
        assertEquals(EntryKind.ARCHIVE, catalog.classpath().entries().get(0).kind());
    }

    @Test
    void gathersFindingsFromAssemblyAndFromReading() {
        Path broken = EngineFixture.jar(workspace, "lib/broken.jar",
                EngineFixture.entries("com/acme/broken/Broken.class", new byte[] {1, 2, 3}));
        ArtifactCatalog catalog = read(List.of(broken), List.of());

        assertEquals(List.of("JP3004"), EngineFixture.codes(catalog.findings()));
    }

    /**
     * The class tally counts what was really read, so a class two artifacts both declare is counted
     * in each of them: both copies were opened, and the shadowed one cost the same work as the winner.
     */
    @Test
    void countsEveryClassItIndexedOncePerPosition() {
        ArtifactCatalog catalog = read(List.of(jar("lib/winner.jar", SHARED)), List.of(jar("lib/loser.jar", SHARED)));

        assertEquals(2, catalog.analyzedClassCount());
        assertEquals(2, catalog.artifacts().size());
        assertEquals(1, catalog.declarations().size());
    }

    @Test
    void countsNoClassesForAPositionThatDeclaresNone() {
        Path resources = EngineFixture.jar(workspace, "lib/resources.jar",
                EngineFixture.entries("META-INF/NOTICE", new byte[] {10}));
        ArtifactCatalog catalog = read(List.of(resources), List.of());

        assertEquals(0, catalog.analyzedClassCount());
        assertEquals(1, catalog.artifacts().size());
    }

    @Test
    void keepsBothIndexesUnmodifiable() {
        ArtifactCatalog catalog = read(List.of(jar("app.jar", "com/acme/App")), List.of());

        assertTrue(catalog.artifacts().size() == 1);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> catalog.declarations().clear());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> catalog.artifactsByPackage().clear());
    }

    private ArtifactCatalog read(List<Path> applications, List<Path> classpath) {
        return ArtifactCatalog.read(
                EngineFixture.request(applications, classpath, 17), new ResourceBudget());
    }

    private Path jar(String name, String internalName) {
        return EngineFixture.jar(workspace, name,
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName)));
    }
}
