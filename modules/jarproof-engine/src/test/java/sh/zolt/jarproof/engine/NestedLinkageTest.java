package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;

/** Linkage across the boundary between an application archive and the libraries it carries. */
final class NestedLinkageTest {
    private static final String REPORT = "com/acme/app/OrderReport";
    private static final String POLICY = "com/acme/lib/OrderPolicy";
    private static final String DESCRIBE = "describe";
    private static final String TAKES_TEXT = "(Ljava/lang/String;)V";
    private static final String NO_ARGUMENTS = "()V";
    private static final String LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "api.jar";
    private static final String OTHER_LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "consumer.jar";
    private static final String WIDGET = "com/acme/lib/Widget";
    private static final String APPLICATION = "app.jar";

    @TempDir
    Path workspace;

    @Test
    void reportsWhatTheApplicationsOwnClassesCannotReachInACarriedLibrary() {
        Path application = broken();

        List<Finding> findings = EngineFixture.verify(List.of(application), List.of(), 17);

        Finding finding = EngineFixture.required(findings, "JP1003");
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(POLICY + "#" + DESCRIBE + TAKES_TEXT, finding.subject());
        assertEquals(application + "!/BOOT-INF/classes", finding.artifact().artifact());
        assertEquals(REPORT + ".class", finding.artifact().classEntry().orElseThrow());
    }

    @Test
    void namesTheCarriedLibraryTheReferenceResolvedInAsEvidence() {
        Path application = broken();

        Finding finding = EngineFixture.required(
                EngineFixture.verify(List.of(application), List.of(), 17), "JP1003");

        assertTrue(EngineFixture.evidence(finding).stream()
                        .anyMatch(line -> line.contains(application + "!/" + LIBRARY)),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void treatsALibraryTheApplicationCarriesAsALibrary() {
        Path application = libraryToLibrary();

        List<Finding> narrow = LinkageFixture.check(List.of(application), List.of(), Scope.APPLICATION);
        List<Finding> wide = LinkageFixture.check(List.of(application), List.of(), Scope.ALL);

        assertEquals(List.of(), EngineFixture.codes(narrow));
        assertEquals(List.of("JP1003"), EngineFixture.codes(wide));
        assertEquals(Severity.WARNING, wide.get(0).severity());
        assertEquals(application + "!/" + OTHER_LIBRARY, wide.get(0).artifact().artifact());
    }

    @Test
    void selectsAVersionedEntryUsingTheCarriedLibrarysOwnManifest() {
        Path application = multiReleaseLibrary();

        ArtifactCatalog catalog = ArtifactCatalog.read(
                EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget());

        assertEquals(
                List.of("META-INF/versions/11/" + WIDGET + ".class"),
                catalog.artifacts().get(1).classes().stream().map(IndexedClass::entryName).toList());
        assertEquals(
                List.of(EngineFixture.JAVA_17_MAJOR),
                catalog.artifacts().get(1).classes().stream().map(IndexedClass::classFileMajor).toList());
    }

    @Test
    void reportsAVersionedEntryTheCarriedLibraryNeverAnnounced() {
        Path application = layout()
                .withLibrary(LIBRARY, versionedEntries(EngineFixture.manifest()))
                .write(workspace, APPLICATION);

        Finding finding = EngineFixture.required(
                EngineFixture.verify(List.of(application), List.of(), 17), "JP3003");

        assertEquals(application + "!/" + LIBRARY, finding.artifact().artifact());
    }

    @Test
    void describesTheSameArchiveIdenticallyHoweverItWasBuilt() {
        Path first = layout()
                .withIndex(BootLayoutFixture.INDEX_PATH, LIBRARY, OTHER_LIBRARY)
                .withLibrary(LIBRARY, declaringPolicy())
                .withLibrary(OTHER_LIBRARY, callingPolicy())
                .write(workspace, APPLICATION);
        Path shuffled = BootLayoutFixture.archive()
                .withLibrary(OTHER_LIBRARY, callingPolicy())
                .withLibrary(LIBRARY, declaringPolicy())
                .with(BootLayoutFixture.CLASSES_ROOT + REPORT + ".class", caller())
                .withIndex(BootLayoutFixture.INDEX_PATH, LIBRARY, OTHER_LIBRARY)
                .write(workspace, "shuffled/" + APPLICATION);

        List<String> ordered = subjects(first);
        List<String> reordered = subjects(shuffled);

        assertEquals(ordered, reordered);
        assertEquals(
                List.of(
                        "JP1003 " + POLICY + "#" + DESCRIBE + TAKES_TEXT,
                        "JP2003 com/acme/lib"),
                ordered,
                "one library calling what another does not declare, and the package they share");
    }

    private List<String> subjects(Path application) {
        return EngineFixture.verify(List.of(application), List.of(), 17).stream()
                .map(finding -> finding.code().value() + " " + finding.subject())
                .toList();
    }

    /** An application whose own class calls a descriptor the library it carries does not declare. */
    private Path broken() {
        return layout().withLibrary(LIBRARY, declaringPolicy()).write(workspace, APPLICATION);
    }

    /** Two carried libraries, one calling what the other does not declare. */
    private Path libraryToLibrary() {
        return BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + "com/acme/app/Quiet.class",
                        EngineFixture.classFile("com/acme/app/Quiet"))
                .withLibrary(LIBRARY, declaringPolicy())
                .withLibrary(OTHER_LIBRARY, callingPolicy())
                .write(workspace, APPLICATION);
    }

    private Path multiReleaseLibrary() {
        Manifest announced = EngineFixture.manifest();
        announced.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        return layout().withLibrary(LIBRARY, versionedEntries(announced)).write(workspace, APPLICATION);
    }

    private static Map<String, byte[]> versionedEntries(Manifest manifest) {
        Map<String, byte[]> entries = EngineFixture.entries();
        entries.put(JarFile.MANIFEST_NAME, EngineFixture.manifestBytes(manifest));
        entries.put(WIDGET + ".class", EngineFixture.classFile(WIDGET, Opcodes.V1_8));
        entries.put("META-INF/versions/11/" + WIDGET + ".class", EngineFixture.classFile(WIDGET));
        return entries;
    }

    private BootLayoutFixture layout() {
        return BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + REPORT + ".class", caller());
    }

    private static Map<String, byte[]> declaringPolicy() {
        return EngineFixture.entries(
                POLICY + ".class",
                LinkageFixture.type(POLICY, LinkageFixture.PUBLIC_CLASS, EngineFixture.OBJECT, List.of(),
                        List.of(LinkageFixture.member(DESCRIBE, NO_ARGUMENTS, Opcodes.ACC_PUBLIC))));
    }

    private static Map<String, byte[]> callingPolicy() {
        return EngineFixture.entries("com/acme/lib/Consumer.class", LinkageFixture.caller(
                "com/acme/lib/Consumer", List.of(reference())));
    }

    private static byte[] caller() {
        return LinkageFixture.caller(REPORT, List.of(reference()));
    }

    private static MemberReference reference() {
        return LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, POLICY, DESCRIBE, TAKES_TEXT);
    }
}
