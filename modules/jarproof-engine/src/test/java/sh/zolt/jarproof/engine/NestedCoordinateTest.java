package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;

/**
 * The publication identity a library nested inside an application archive claims for itself, and what
 * JP2004 sees once it has one. The boundary is the point: a Boot-packaged library and an ordinary
 * classpath entry can be two copies of one published artifact, and that is exactly where a version
 * conflict hides today.
 */
final class NestedCoordinateTest {
    private static final String CODE = "JP2004";
    private static final String ORDERS = "com/acme/app/Orders";
    private static final String CODEC = "com/acme/lib/Codec";
    private static final String GEARS = "com/acme/lib/Gears";
    private static final String WIDGET = "com/acme/lib/Widget";
    private static final String GROUP = "com.acme";
    private static final String WIDGETS = "widgets";
    private static final String GEARS_ARTIFACT = "gears";
    private static final String OLDER = "1.0.0";
    private static final String NEWER = "2.0.0";
    private static final String IDENTITY = GROUP + ":" + WIDGETS;
    private static final String CLAIMED_BY = " is claimed by ";
    private static final String FIRST_LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "first.jar";
    private static final String SECOND_LIBRARY = BootLayoutFixture.LIBRARY_DIRECTORY + "second.jar";
    private static final String APPLICATION = "app.jar";
    private static final String CLASSPATH_ARCHIVE = "lib/widgets-2.0.0.jar";

    @TempDir
    Path workspace;

    @Test
    void reportsTwoNestedLibrariesClaimingOneCoordinateAtDifferentVersions() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, published(CODEC, WIDGETS, OLDER))
                .withLibrary(SECOND_LIBRARY, published(GEARS, WIDGETS, NEWER))
                .write(workspace, APPLICATION);

        Finding finding = EngineFixture.required(verify(application), CODE);

        assertEquals(IDENTITY, finding.subject());
        assertEquals(nested(application, FIRST_LIBRARY), finding.artifact().artifact());
        assertEquals(
                List.of(
                        OLDER + CLAIMED_BY + nested(application, FIRST_LIBRARY),
                        NEWER + CLAIMED_BY + nested(application, SECOND_LIBRARY)),
                EngineFixture.evidence(finding));
    }

    @Test
    void reportsTheSameCoordinateOnBothSidesOfTheNestedBoundary() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, published(CODEC, WIDGETS, OLDER))
                .write(workspace, APPLICATION);
        Path classpath = EngineFixture.jar(workspace, CLASSPATH_ARCHIVE, published(GEARS, WIDGETS, NEWER));

        Finding finding = EngineFixture.required(
                EngineFixture.verify(List.of(application), List.of(classpath), 17), CODE);

        assertEquals(IDENTITY, finding.subject());
        assertEquals(nested(application, FIRST_LIBRARY), finding.artifact().artifact());
        assertEquals(
                List.of(
                        OLDER + CLAIMED_BY + nested(application, FIRST_LIBRARY),
                        NEWER + CLAIMED_BY + classpath),
                EngineFixture.evidence(finding));
    }

    @Test
    void staysQuietWhenEveryNestedLibraryClaimsAnIdentityOfItsOwn() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, published(CODEC, WIDGETS, OLDER))
                .withLibrary(SECOND_LIBRARY, published(GEARS, GEARS_ARTIFACT, OLDER))
                .write(workspace, APPLICATION);

        assertEquals(Optional.empty(), EngineFixture.coded(verify(application), CODE));
        assertEquals(List.of(IDENTITY, GROUP + ":" + GEARS_ARTIFACT), claimedIdentities(application));
    }

    @Test
    void staysQuietAboutANestedLibraryClaimingSeveralIdentities() {
        Map<String, byte[]> shaded = published(CODEC, WIDGETS, OLDER);
        shaded.put(recordPath(GEARS_ARTIFACT), properties(GEARS_ARTIFACT, "9.0.0"));
        Path application = layout().withLibrary(FIRST_LIBRARY, shaded).write(workspace, APPLICATION);
        Path classpath = EngineFixture.jar(workspace, CLASSPATH_ARCHIVE, published(WIDGET, WIDGETS, NEWER));

        List<Finding> findings = EngineFixture.verify(List.of(application), List.of(classpath), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, CODE));
        assertEquals(Optional.empty(), library(application).coordinate());
    }

    @Test
    void staysQuietAboutANestedRecordThatLeavesPartOfTheCoordinateOut() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, recording(CODEC, "artifactId=" + WIDGETS + "\nversion=" + OLDER + "\n"))
                .withLibrary(SECOND_LIBRARY, recording(GEARS, "groupId=" + GROUP + "\nartifactId=" + WIDGETS + "\n"))
                .write(workspace, APPLICATION);
        Path classpath = EngineFixture.jar(workspace, CLASSPATH_ARCHIVE, published(WIDGET, WIDGETS, NEWER));

        List<Finding> findings = EngineFixture.verify(List.of(application), List.of(classpath), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, CODE));
        assertEquals(List.of(), claimedIdentities(application));
    }

    @Test
    void staysQuietAboutANestedManifestThatNamesATitleAndNoVersion() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, EngineFixture.withManifest(classes(CODEC), titledManifest()))
                .write(workspace, APPLICATION);

        assertEquals(Optional.empty(), library(application).coordinate());
    }

    @Test
    void fallsBackToTheIdentityTheNestedManifestClaims() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, titled(CODEC, OLDER))
                .withLibrary(SECOND_LIBRARY, titled(GEARS, NEWER))
                .write(workspace, APPLICATION);

        Finding finding = EngineFixture.required(verify(application), CODE);

        assertEquals(":" + WIDGETS, finding.subject());
        assertEquals(
                new ArtifactCoordinate("", WIDGETS, OLDER),
                library(application).coordinate().orElseThrow());
    }

    @Test
    void chargesTheRunForTheRecordItReadsOutOfTheArchive() {
        Path application = layout()
                .withLibrary(FIRST_LIBRARY, published(CODEC, WIDGETS, OLDER))
                .write(workspace, APPLICATION);
        ResourceBudget budget = budgetedUpToTheRecord(application);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ArtifactCatalog.read(
                        EngineFixture.request(List.of(application), List.of(), 17), budget));

        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_EXPANDED_BYTES)),
                failure::getMessage);
        assertTrue(failure.getMessage().endsWith(recordPath(WIDGETS)), failure::getMessage);
    }

    /**
     * A budget with room for everything the run reads before the record and not one byte more: the
     * classes root's only class file, then the nested library read whole out of the outer archive. The
     * next charge is the record itself, so the ceiling names it.
     */
    private static ResourceBudget budgetedUpToTheRecord(Path application) {
        long before = EngineFixture.classFile(ORDERS).length + declaredBytes(application, FIRST_LIBRARY);
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES - before, FIRST_LIBRARY);
        return budget;
    }

    private static long declaredBytes(Path application, String entryName) {
        try (ZipFile archive = new ZipFile(application.toFile())) {
            return archive.getEntry(entryName).getSize();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private BootLayoutFixture layout() {
        return BootLayoutFixture.archive()
                .with(BootLayoutFixture.CLASSES_ROOT + ORDERS + ".class", EngineFixture.classFile(ORDERS));
    }

    /**
     * One library's worth of entries the way Maven publishes them: a class, the project file that
     * carries no coordinate of its own, and the record that does.
     */
    private static Map<String, byte[]> published(String internalName, String artifact, String version) {
        Map<String, byte[]> entries = classes(internalName);
        entries.put(mavenPath(artifact, "pom.xml"), bytes("<project/>"));
        entries.put(recordPath(artifact), properties(artifact, version));
        return entries;
    }

    /** One library's worth of entries whose record is present but says too little to be a coordinate. */
    private static Map<String, byte[]> recording(String internalName, String properties) {
        Map<String, byte[]> entries = classes(internalName);
        entries.put(recordPath(WIDGETS), bytes(properties));
        return entries;
    }

    /** One library's worth of entries whose only claim is the weaker one its manifest makes. */
    private static Map<String, byte[]> titled(String internalName, String version) {
        Manifest manifest = titledManifest();
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        return EngineFixture.withManifest(classes(internalName), manifest);
    }

    private static Manifest titledManifest() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, WIDGETS);
        return manifest;
    }

    private static Map<String, byte[]> classes(String internalName) {
        return EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName));
    }

    private static String recordPath(String artifact) {
        return mavenPath(artifact, "pom.properties");
    }

    private static String mavenPath(String artifact, String fileName) {
        return "META-INF/maven/" + GROUP + "/" + artifact + "/" + fileName;
    }

    private static byte[] properties(String artifact, String version) {
        return bytes("groupId=" + GROUP + "\nartifactId=" + artifact + "\nversion=" + version + "\n");
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static String nested(Path application, String entryName) {
        return application + "!/" + entryName;
    }

    private List<Finding> verify(Path application) {
        return EngineFixture.verify(List.of(application), List.of(), 17);
    }

    /** The first nested library of the application, which every case here puts its claim in. */
    private IndexedArtifact library(Path application) {
        return read(application).artifacts().get(1);
    }

    private List<String> claimedIdentities(Path application) {
        return read(application).artifacts().stream()
                .map(IndexedArtifact::coordinate)
                .flatMap(Optional::stream)
                .map(ArtifactCoordinate::name)
                .toList();
    }

    private ArtifactCatalog read(Path application) {
        return ArtifactCatalog.read(
                EngineFixture.request(List.of(application), List.of(), 17), new ResourceBudget());
    }
}
