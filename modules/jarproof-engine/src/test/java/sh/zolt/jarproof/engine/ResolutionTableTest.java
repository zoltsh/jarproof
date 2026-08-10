package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResolutionTableTest {
    private static final String STRING = "java/lang/String";
    private static final String THING = "com/acme/lib/Thing";
    private static final String FIRST = "com/acme/lib/First";
    private static final String SECOND = "com/acme/lib/Second";
    private static final String TOP = "com/acme/lib/Top";
    private static final String MIDDLE = "com/acme/lib/Middle";
    private static final String OTHER = "com/acme/lib/Other";
    private static final String HOLDER = "com/acme/lib/Holder";
    private static final String CHAIN = "com/acme/deep/Level";
    private static final int DEEPER_THAN_THE_CEILING = 300;
    private static final int HIERARCHY_CEILING = 256;

    @TempDir
    Path workspace;

    @Test
    void answersFromTheTargetRuntimeBeforeTheClasspath() {
        ResolutionTable table = table(LinkageFixture.classes(STRING, LinkageFixture.type(
                STRING,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("nonsense", "()V", LinkageFixture.METAFACTORY)))));

        ResolvedClass resolved = table.find(STRING).orElseThrow();

        assertTrue(resolved.fromPlatform());
        assertEquals(Optional.of("java.base"), resolved.platformModule());
        assertEquals(Optional.empty(), resolved.declared("nonsense", "()V"));
    }

    @Test
    void answersWithTheWinningClasspathDeclaration() {
        Path winner = jar("lib/winner.jar", LinkageFixture.classes(THING, plain(THING)));
        Path loser = jar("lib/loser.jar", LinkageFixture.classes(THING, plain(THING)));

        ResolvedClass resolved = LinkageFixture.table(List.of(winner), List.of(loser)).find(THING).orElseThrow();

        assertFalse(resolved.fromPlatform());
        assertEquals(winner.toString(), resolved.declaration().orElseThrow().artifactPath());
        assertEquals(THING, resolved.internalName());
    }

    @Test
    void stopsAWalkThatLoopsBackOnItself() {
        ResolutionTable table = table(LinkageFixture.and(
                LinkageFixture.classes(FIRST, extending(FIRST, SECOND)), SECOND, extending(SECOND, FIRST)));

        List<String> chain = names(table.classChain(table.find(FIRST).orElseThrow()));

        assertEquals(List.of(FIRST, SECOND), chain);
    }

    @Test
    void stopsAWalkWhereResolutionStops() {
        ResolutionTable table = table(LinkageFixture.classes(FIRST, extending(FIRST, "com/acme/lib/Gone")));

        assertEquals(List.of(FIRST), names(table.classChain(table.find(FIRST).orElseThrow())));
    }

    @Test
    void stopsAWalkDeeperThanTheHierarchyCeiling() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int level = 0; level < DEEPER_THAN_THE_CEILING; level++) {
            String parent = level == DEEPER_THAN_THE_CEILING - 1 ? EngineFixture.OBJECT : CHAIN + (level + 1);
            LinkageFixture.and(entries, CHAIN + level, extending(CHAIN + level, parent));
        }
        ResolutionTable table = table(entries);

        assertEquals(HIERARCHY_CEILING, table.classChain(table.find(CHAIN + 0).orElseThrow()).size());
    }

    @Test
    void collectsSuperinterfacesTransitivelyInDeclaredOrder() {
        Map<String, byte[]> entries = LinkageFixture.classes(TOP, anInterface(TOP, List.of()));
        LinkageFixture.and(entries, MIDDLE, anInterface(MIDDLE, List.of(TOP)));
        LinkageFixture.and(entries, OTHER, anInterface(OTHER, List.of()));
        LinkageFixture.and(entries, HOLDER, LinkageFixture.type(
                HOLDER, LinkageFixture.PUBLIC_CLASS, EngineFixture.OBJECT, List.of(MIDDLE, OTHER), List.of()));
        ResolutionTable table = table(entries);

        List<ResolvedClass> chain = table.classChain(table.find(HOLDER).orElseThrow());

        assertEquals(List.of(MIDDLE, OTHER, TOP), names(table.interfaceClosure(chain)));
    }

    @Test
    void stopsAnInterfaceWalkDeeperThanTheHierarchyCeiling() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int level = 0; level < DEEPER_THAN_THE_CEILING; level++) {
            List<String> parent = level == DEEPER_THAN_THE_CEILING - 1 ? List.of() : List.of(CHAIN + (level + 1));
            LinkageFixture.and(entries, CHAIN + level, anInterface(CHAIN + level, parent));
        }
        ResolutionTable table = table(entries);

        List<ResolvedClass> chain = List.of(table.find(CHAIN + 0).orElseThrow());

        assertEquals(HIERARCHY_CEILING, table.interfaceClosure(chain).size());
    }

    @Test
    void keepsQuietAboutAClaimNoParserCouldRead() {
        ResolutionTable table = table(LinkageFixture.and(
                LinkageFixture.classes(FIRST, Arrays.copyOf(plain(FIRST), 8)), SECOND, plain(SECOND)));

        assertTrue(table.claimedWithoutShape(FIRST));
        assertEquals(Optional.empty(), table.find(FIRST));
        assertFalse(table.claimedWithoutShape(SECOND));
        assertFalse(table.claimedWithoutShape("com/acme/lib/Never"));
    }

    private static byte[] plain(String internalName) {
        return LinkageFixture.type(
                internalName, LinkageFixture.PUBLIC_CLASS, EngineFixture.OBJECT, List.of(), List.of());
    }

    private static byte[] extending(String internalName, String superName) {
        return LinkageFixture.type(internalName, LinkageFixture.PUBLIC_CLASS, superName, List.of(), List.of());
    }

    private static byte[] anInterface(String internalName, List<String> interfaces) {
        return LinkageFixture.type(
                internalName, LinkageFixture.PUBLIC_INTERFACE, EngineFixture.OBJECT, interfaces, List.of());
    }

    private static List<String> names(List<ResolvedClass> resolved) {
        return resolved.stream().map(ResolvedClass::internalName).toList();
    }

    private ResolutionTable table(Map<String, byte[]> entries) {
        return LinkageFixture.table(List.of(jar("lib/library-1.0.jar", entries)), List.of());
    }

    private Path jar(String name, Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, name, entries);
    }
}
