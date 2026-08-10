package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;

final class ServiceDeclarationReaderTest {
    private static final String OTHER_SERVICE = "com.acme.codec.Filter";
    private static final String PATHOLOGICAL = ServiceDeclaration.RESOURCE_PREFIX + ServiceFixture.SERVICE;

    @TempDir
    Path workspace;

    @Test
    void readsAServiceFileFromAClassDirectory() {
        Path directory = EngineFixture.classDirectory(workspace, "classes", ServiceFixture.serviceFile(
                classes(), ServiceFixture.SERVICE, ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER)));

        Finding finding = EngineFixture.required(ServiceFixture.findings(directory), "JP4001");

        assertEquals(directory.toString(), finding.artifact().artifact());
        assertEquals(ServiceFixture.SERVICE_ENTRY, finding.artifact().classEntry().orElseThrow());
    }

    @Test
    void readsEveryClasspathEntryInSearchOrder() {
        Path application = EngineFixture.jar(workspace, "app.jar", ServiceFixture.serviceFile(
                classes(), ServiceFixture.SERVICE, ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER)));
        Path library = EngineFixture.jar(workspace, "lib/library.jar", ServiceFixture.serviceFile(
                EngineFixture.entries(), OTHER_SERVICE, ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER)));

        List<Finding> findings = ServiceFixture.findings(List.of(application), List.of(library));

        assertEquals(List.of("JP4001", "JP4001"), EngineFixture.codes(findings));
        assertEquals(application.toString(), findings.get(0).artifact().artifact());
        assertEquals(library.toString(), findings.get(1).artifact().artifact());
    }

    @Test
    void readsSeveralConfigurationFilesFromOneArchiveInEntryOrder() {
        Map<String, byte[]> entries = ServiceFixture.serviceFile(
                classes(), OTHER_SERVICE, ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER));
        Path archive = EngineFixture.jar(workspace, "lib/two-services.jar", ServiceFixture.serviceFile(
                entries, ServiceFixture.SERVICE, ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER)));

        List<Finding> findings = ServiceFixture.findings(archive);

        assertEquals(
                List.of(ServiceFixture.SERVICE_ENTRY, ServiceDeclaration.RESOURCE_PREFIX + OTHER_SERVICE),
                findings.stream().map(finding -> finding.artifact().classEntry().orElseThrow()).toList());
    }

    @Test
    void ignoresEntriesThatAreNotConfigurationFilesDirectlyInTheServicesDirectory() {
        Map<String, byte[]> entries = classes();
        entries.put(ServiceDeclaration.RESOURCE_PREFIX, new byte[0]);
        entries.put(ServiceDeclaration.RESOURCE_PREFIX + "nested/" + ServiceFixture.SERVICE,
                ServiceFixture.utf8(ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER)));
        Path archive = EngineFixture.jar(workspace, "lib/nested-services.jar", entries);

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void ignoresVersionedConfigurationFiles() {
        Map<String, byte[]> entries = classes();
        entries.put(ArchiveLayout.VERSIONS_PREFIX + "9/" + ServiceFixture.SERVICE_ENTRY,
                ServiceFixture.utf8(ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER)));
        Path archive = EngineFixture.jar(workspace, "lib/versioned-services.jar",
                EngineFixture.withManifest(entries, multiRelease()));

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void ignoresAClassDirectoryWithoutAServicesDirectory() {
        Path directory = EngineFixture.classDirectory(workspace, "plain-classes", classes());

        assertEquals(List.of(), ServiceFixture.findings(directory));
    }

    @Test
    void ignoresAnArchiveWithoutAServicesDirectory() {
        Path archive = EngineFixture.jar(workspace, "lib/plain.jar", classes());

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void refusesAConfigurationFileThatCompressesTooWellToBeReal() {
        Path archive = EngineFixture.jar(workspace, "lib/pathological.jar",
                ServiceFixture.serviceFile(classes(), ServiceFixture.SERVICE, new byte[300_000]));

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> ServiceFixture.findings(archive));

        assertTrue(failure.getMessage().contains(PATHOLOGICAL), failure.getMessage());
        assertTrue(failure.getMessage().contains(String.valueOf(ResourceBudget.MAXIMUM_COMPRESSION_RATIO)),
                failure.getMessage());
    }

    @Test
    void chargesTheBudgetTheCallerSupplies() {
        Path archive = EngineFixture.jar(workspace, "lib/charged.jar", ServiceFixture.serviceFile(
                classes(), ServiceFixture.SERVICE, ServiceFixture.lines(ServiceFixture.PROVIDER)));
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES, PATHOLOGICAL);

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> ServiceFixture.findings(archive, budget));

        assertTrue(failure.getMessage().contains(PATHOLOGICAL), failure.getMessage());
    }

    @Test
    void chargesAClassDirectoryReadAgainstTheSameBudget() {
        Path directory = EngineFixture.classDirectory(workspace, "counted", ServiceFixture.serviceFile(
                classes(), ServiceFixture.SERVICE, ServiceFixture.lines(ServiceFixture.PROVIDER)));
        ResourceBudget budget = new ResourceBudget();
        budget.addExpandedBytes(ResourceBudget.MAXIMUM_EXPANDED_BYTES, PATHOLOGICAL);

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> ServiceFixture.findings(directory, budget));

        assertTrue(failure.getMessage().contains(PATHOLOGICAL), failure.getMessage());
    }

    private static Manifest multiRelease() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        return manifest;
    }

    private Map<String, byte[]> classes() {
        Map<String, byte[]> entries = EngineFixture.entries(
                ServiceFixture.SERVICE_INTERNAL + ".class",
                ServiceFixture.serviceInterface(ServiceFixture.SERVICE_INTERNAL));
        entries.put(ServiceFixture.PROVIDER_INTERNAL + ".class",
                ServiceFixture.provider(ServiceFixture.PROVIDER_INTERNAL, ServiceFixture.SERVICE_INTERNAL));
        return entries;
    }
}
