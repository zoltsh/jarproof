package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

/** The vocabulary of a nested layout: what a manifest declares, and what its classpath index says. */
final class BootLayoutTest {
    @Test
    void readsOnlyTheIndexLinesItCanParse() {
        assertEquals(
                List.of("BOOT-INF/lib/one.jar", "WEB-INF/lib/two.jar"),
                BootLayout.indexedPaths("- \"BOOT-INF/lib/one.jar\"\n\n- \"WEB-INF/lib/two.jar\"\n"));
        assertEquals(List.of("bare.jar"), BootLayout.indexedPaths("- bare.jar\n"));
        assertEquals(List.of("\"unterminated.jar"), BootLayout.indexedPaths("- \"unterminated.jar\n"));
        assertEquals(List.of("x"), BootLayout.indexedPaths("- x\n"));
        assertEquals(List.of(), BootLayout.indexedPaths("BOOT-INF/lib/unshaped.jar\n- \n"));
    }

    @Test
    void normalizesADeclaredAreaAndIgnoresABlankOne() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(new Attributes.Name(BootLayoutFixture.DECLARED_CLASSES), "classes");
        manifest.getMainAttributes().put(new Attributes.Name(BootLayoutFixture.DECLARED_LIBRARIES), " ");

        Optional<BootLayout> layout = BootLayout.of(Optional.of(manifest), List.of("classes/A.class"));

        assertEquals(Optional.empty(), layout, "a blank declaration is no declaration");
        assertTrue(BootLayout.of(Optional.of(manifest), List.of(BootLayoutFixture.CLASSES_ROOT + "A.class"))
                .filter(fallback -> fallback.classesRoot().equals("classes/"))
                .isPresent(),
                "the one declared area still overrides the fallback the entries chose");
    }

    @Test
    void namesNoLayoutForAnOrdinaryArchive() {
        assertEquals(
                Optional.empty(),
                BootLayout.of(Optional.of(EngineFixture.manifest()), List.of("com/acme/lib/Codec.class")));
        assertEquals(Optional.empty(), BootLayout.of(Optional.empty(), List.of()));
    }

    @Test
    void countsOnlyArchiveSuffixesAsNestedArchives() {
        assertTrue(ArchiveLayout.isNestedArchive("BOOT-INF/lib/one.jar"));
        assertTrue(ArchiveLayout.isNestedArchive("nested/data.zip"));
        assertTrue(ArchiveLayout.isNestedArchive("SHOUTY.JAR"));
        assertFalse(ArchiveLayout.isNestedArchive("com/acme/app/Orders.class"));
    }
}
