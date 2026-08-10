package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Severity;

final class MultiReleaseSelectionTest {
    private static final String WIDGET = "com/acme/mr/Widget";
    private static final String WIDGET_ENTRY = "com/acme/mr/Widget.class";
    private static final String LATE = "com/acme/mr/Late";

    @TempDir
    Path workspace;

    @Test
    void selectsTheHighestVersionAtOrBelowTheTarget() {
        Path archive = multiRelease(true);

        assertEquals(
                List.of("META-INF/versions/11/" + WIDGET_ENTRY),
                entryNames(archive, 11));
    }

    @Test
    void selectsANewerVersionForANewerTarget() {
        Path archive = multiRelease(true);

        assertTrue(entryNames(archive, 17).contains("META-INF/versions/17/" + WIDGET_ENTRY),
                entryNames(archive, 17).toString());
    }

    @Test
    void fallsBackToTheBaseEntryWhenNoVersionFits() {
        Path archive = multiRelease(true);

        assertEquals(List.of(WIDGET_ENTRY), entryNames(archive, 9));
    }

    @Test
    void acceptsAClassOnlyAVersionedEntryDeclares() {
        Path archive = multiRelease(true);

        assertTrue(entryNames(archive, 17).contains("META-INF/versions/17/" + LATE + ".class"),
                entryNames(archive, 17).toString());
    }

    @Test
    void ignoresVersionedEntriesTheManifestNeverAnnounced() {
        Path archive = multiRelease(false);

        List<Finding> findings = EngineFixture.verify(List.of(archive), List.of(), 17);
        Finding finding = EngineFixture.required(findings, "JP3003");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(List.of(WIDGET_ENTRY), entryNames(archive, 17));
        assertTrue(finding.subject().startsWith(ArchiveLayout.VERSIONS_PREFIX), finding.subject());
        assertEquals(Optional.empty(), finding.artifact().classEntry());
    }

    @Test
    void reportsAVersionDirectoryNoRuntimeCanSelect() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        Map<String, byte[]> entries = EngineFixture.entries(WIDGET_ENTRY, base());
        entries.put("META-INF/versions/eight/" + WIDGET_ENTRY, base());
        entries.put("META-INF/versions/8/" + WIDGET_ENTRY, base());
        Path archive = EngineFixture.jar(workspace, "lib/odd.jar", EngineFixture.withManifest(entries, manifest));

        List<String> subjects = EngineFixture.verify(List.of(archive), List.of(), 17).stream()
                .filter(finding -> finding.code().value().equals("JP3003"))
                .map(Finding::subject)
                .toList();

        assertEquals(List.of("META-INF/versions/8", "META-INF/versions/eight"), subjects);
    }

    @Test
    void treatsABareVersionsPrefixAsAnUnusableDirectory() {
        MultiReleaseSelection unannounced =
                MultiReleaseSelection.base(List.of(ArchiveLayout.VERSIONS_PREFIX), "bare.jar");
        MultiReleaseSelection announced =
                MultiReleaseSelection.versioned(List.of(ArchiveLayout.VERSIONS_PREFIX), 17, "bare.jar");

        assertEquals(Map.of(), unannounced.classEntries());
        assertEquals(1, unannounced.layoutFindings().size());
        assertEquals(1, announced.layoutFindings().size());
    }

    @Test
    void treatsAnUnparsableVersionNumberAsUnusable() {
        MultiReleaseSelection selection = MultiReleaseSelection.versioned(
                List.of("META-INF/versions/99999999999999/" + WIDGET_ENTRY), 17, "huge.jar");

        assertEquals(Map.of(), selection.classEntries());
        assertEquals(1, selection.layoutFindings().size());
    }

    @Test
    void keepsTheHighestVersionEvenWhenALowerOneIsReadLater() {
        MultiReleaseSelection selection = MultiReleaseSelection.versioned(
                List.of("META-INF/versions/11/" + WIDGET_ENTRY, "META-INF/versions/9/" + WIDGET_ENTRY),
                17,
                "reordered.jar");

        assertEquals(Map.of(WIDGET_ENTRY, "META-INF/versions/11/" + WIDGET_ENTRY), selection.classEntries());
    }

    @Test
    void ignoresAVersionDirectoryThatHoldsNoEntry() {
        MultiReleaseSelection selection =
                MultiReleaseSelection.versioned(List.of("META-INF/versions/11"), 17, "empty.jar");

        assertEquals(Map.of(), selection.classEntries());
        assertEquals(List.of(), selection.layoutFindings());
    }

    private List<String> entryNames(Path archive, int release) {
        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(List.of(archive), List.of(), release), new ResourceBudget());
        return catalog.artifacts().get(0).classes().stream().map(IndexedClass::entryName).toList();
    }

    private Path multiRelease(boolean announced) {
        Manifest manifest = EngineFixture.manifest();
        if (announced) {
            manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "True");
        }
        Map<String, byte[]> entries = EngineFixture.entries(WIDGET_ENTRY, base());
        entries.put("META-INF/versions/11/" + WIDGET_ENTRY, base());
        entries.put("META-INF/versions/17/" + WIDGET_ENTRY, base());
        entries.put("META-INF/versions/17/" + LATE + ".class", EngineFixture.classFile(LATE, Opcodes.V1_8));
        return EngineFixture.jar(workspace, "lib/multi-" + announced + ".jar",
                EngineFixture.withManifest(entries, manifest));
    }

    private static byte[] base() {
        return EngineFixture.classFile(WIDGET, Opcodes.V1_8);
    }
}
