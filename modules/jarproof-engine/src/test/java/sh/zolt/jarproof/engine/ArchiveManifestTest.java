package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

final class ArchiveManifestTest {
    @Test
    void readsAMultiReleaseAnnouncementWhateverItsCase() {
        assertTrue(ArchiveManifest.isMultiRelease(Optional.of(withMain(Attributes.Name.MULTI_RELEASE, "TRUE"))));
        assertFalse(ArchiveManifest.isMultiRelease(Optional.of(withMain(Attributes.Name.MULTI_RELEASE, "false"))));
        assertFalse(ArchiveManifest.isMultiRelease(Optional.of(EngineFixture.manifest())));
        assertFalse(ArchiveManifest.isMultiRelease(Optional.empty()));
    }

    @Test
    void splitsAClassPathOnAnyRunOfWhitespace() {
        Manifest manifest = withMain(Attributes.Name.CLASS_PATH, "  first.jar   second.jar ");

        assertEquals(List.of("first.jar", "second.jar"), ArchiveManifest.classPath(Optional.of(manifest)));
        assertEquals(List.of(), ArchiveManifest.classPath(Optional.of(EngineFixture.manifest())));
        assertEquals(List.of(), ArchiveManifest.classPath(Optional.empty()));
    }

    @Test
    void ignoresASectionThatDeclaresItIsNotSealed() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getEntries().put("com/acme/open/", section("false"));

        assertEquals(List.of(), ArchiveManifest.sealedPackages(Optional.of(manifest), List.of()));
    }

    @Test
    void sealsNothingWhenThereIsNoManifest() {
        assertEquals(List.of(), ArchiveManifest.sealedPackages(Optional.empty(), List.of()));
    }

    @Test
    void sealsEveryPackageAnArchiveWideSealCovers() {
        Manifest manifest = withMain(Attributes.Name.SEALED, "true");
        List<IndexedClass> classes = List.of(
                declared("com/acme/one/First"),
                declared("com/acme/two/Second"),
                declared("Loose"));

        assertEquals(List.of("com/acme/one", "com/acme/two"),
                ArchiveManifest.sealedPackages(Optional.of(manifest), classes));
    }

    @Test
    void sealsOnlyTheSectionsThatNameAPackage() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getEntries().put("com/acme/sealed/", section("true"));
        manifest.getEntries().put("", section("true"));

        assertEquals(List.of("com/acme/sealed"), ArchiveManifest.sealedPackages(Optional.of(manifest), List.of()));
    }

    private static Attributes section(String sealed) {
        Attributes attributes = new Attributes();
        attributes.put(Attributes.Name.SEALED, sealed);
        return attributes;
    }

    private static Manifest withMain(Attributes.Name name, String value) {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(name, value);
        return manifest;
    }

    private static IndexedClass declared(String internalName) {
        return new IndexedClass(
                internalName + ArchiveLayout.CLASS_SUFFIX,
                internalName,
                EngineFixture.JAVA_17_MAJOR,
                0,
                "0".repeat(64),
                Optional.empty(),
                ClassReferences.empty());
    }
}
