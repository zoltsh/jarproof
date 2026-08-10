package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class ServiceFileParsingTest {
    private static final String MALFORMED_SUMMARY = "malformed service configuration file";
    private static final String NESTED = "com.acme.codec.Outer$Inner";

    @TempDir
    Path workspace;

    @Test
    void ignoresCommentsBlankLinesAndWhitespaceAroundAName() {
        Path archive = providers("# providers for the codec service\n"
                + "\n"
                + "\t  " + ServiceFixture.PROVIDER + "  \t# the only one\n"
                + "#\n"
                + "   \n");

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void countsEveryPhysicalLineIncludingCommentsAndBlanks() {
        Path archive = providers("# providers\r\n"
                + "\r\n"
                + "   \r\n"
                + ServiceFixture.ABSENT_PROVIDER + "\r\n");

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4001");

        assertTrue(EngineFixture.evidence(finding).contains("the provider is named on line 4"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsALineThatIsNotABinaryClassName() {
        Path archive = providers("not a java identifier!\n");

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4003");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.SERVICE_CONFIGURATION_ERROR, finding.predictedError());
        assertEquals(ServiceFixture.SERVICE_INTERNAL, finding.subject());
        assertEquals(MALFORMED_SUMMARY, finding.summary());
        assertEquals(ServiceFixture.SERVICE_ENTRY, finding.artifact().classEntry().orElseThrow());
        assertEquals(
                List.of("the entry on line 1 is not a binary class name: not a java identifier!"),
                EngineFixture.evidence(finding));
    }

    @Test
    void reportsOneMalformedFileHoweverManyLinesAreBroken() {
        Path archive = providers("com..acme.Codec\n" + "com.acme.Codec.\n" + ServiceFixture.PROVIDER + "\n");

        List<Finding> findings = ServiceFixture.findings(archive);

        assertEquals(List.of("JP4003"), EngineFixture.codes(findings));
        assertEquals(
                List.of("the entry on line 1 is not a binary class name: com..acme.Codec"),
                EngineFixture.evidence(findings.get(0)));
    }

    @Test
    void reportsAFileWhoseOwnNameIsNotABinaryClassName() {
        Map<String, byte[]> entries = ServiceFixture.serviceFile(
                classes(), "not a service", ServiceFixture.lines(ServiceFixture.PROVIDER));
        Path archive = EngineFixture.jar(workspace, "lib/mangled.jar", entries);

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4003");

        assertEquals("not a service", finding.subject());
        assertEquals(ServiceDeclaration.RESOURCE_PREFIX + "not a service",
                finding.artifact().classEntry().orElseThrow());
        assertEquals(MALFORMED_SUMMARY, finding.summary());
        assertEquals(
                List.of("the file name is not a binary class name: not a service"),
                EngineFixture.evidence(finding));
    }

    @Test
    void reportsADuplicateProviderEntryWithEveryLineNumber() {
        Path archive = providers("# providers\n"
                + ServiceFixture.PROVIDER + "\n"
                + "# and again\n"
                + ServiceFixture.PROVIDER + "\n"
                + "\n"
                + ServiceFixture.PROVIDER + "\n");

        List<Finding> findings = ServiceFixture.findings(archive);
        Finding finding = EngineFixture.required(findings, "JP4004");

        assertEquals(List.of("JP4004"), EngineFixture.codes(findings));
        assertEquals(Severity.INFO, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(ServiceFixture.PROVIDER_INTERNAL, finding.subject());
        assertEquals("duplicate service provider entry", finding.summary());
        assertEquals(
                List.of("the provider is first named on line 2", "it is named again on line 4",
                        "it is named again on line 6"),
                EngineFixture.evidence(finding));
    }

    @Test
    void treatsBytesThatAreNotUtf8AsAMalformedLine() {
        Map<String, byte[]> entries = ServiceFixture.serviceFile(
                classes(), ServiceFixture.SERVICE, new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0x0A});
        Path archive = EngineFixture.jar(workspace, "lib/undecodable.jar", entries);

        List<Finding> findings = ServiceFixture.findings(archive);

        assertEquals(List.of("JP4003"), EngineFixture.codes(findings));
    }

    @Test
    void acceptsANestedProviderClassName() {
        Map<String, byte[]> classes = classes();
        classes.put("com/acme/codec/Outer$Inner.class",
                ServiceFixture.provider("com/acme/codec/Outer$Inner", ServiceFixture.SERVICE_INTERNAL));
        Path archive = EngineFixture.jar(workspace, "lib/nested.jar",
                ServiceFixture.serviceFile(classes, ServiceFixture.SERVICE, ServiceFixture.lines(NESTED)));

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void acceptsOnlyDotSeparatedJavaIdentifiers() {
        assertTrue(ServiceBinaryName.isLegal("Codec"));
        assertTrue(ServiceBinaryName.isLegal(ServiceFixture.PROVIDER));
        assertTrue(ServiceBinaryName.isLegal(NESTED));
        assertTrue(ServiceBinaryName.isLegal("_p1.$Codec"));
        assertFalse(ServiceBinaryName.isLegal(""));
        assertFalse(ServiceBinaryName.isLegal(".com.acme.Codec"));
        assertFalse(ServiceBinaryName.isLegal("com.acme.Codec."));
        assertFalse(ServiceBinaryName.isLegal("com..acme.Codec"));
        assertFalse(ServiceBinaryName.isLegal("com.acme Codec"));
        assertFalse(ServiceBinaryName.isLegal("1codec.Codec"));
        assertFalse(ServiceBinaryName.isLegal("com/acme/Codec"));
    }

    @Test
    void convertsABinaryNameIntoTheNameAClassIsLookedUpBy() {
        assertEquals(ServiceFixture.PROVIDER_INTERNAL, ServiceBinaryName.internalName(ServiceFixture.PROVIDER));
        assertEquals("Codec", ServiceBinaryName.internalName("Codec"));
    }

    private Map<String, byte[]> classes() {
        Map<String, byte[]> entries = EngineFixture.entries(
                ServiceFixture.SERVICE_INTERNAL + ".class",
                ServiceFixture.serviceInterface(ServiceFixture.SERVICE_INTERNAL));
        entries.put(ServiceFixture.PROVIDER_INTERNAL + ".class",
                ServiceFixture.provider(ServiceFixture.PROVIDER_INTERNAL, ServiceFixture.SERVICE_INTERNAL));
        return entries;
    }

    private Path providers(String content) {
        return EngineFixture.jar(workspace, "lib/providers.jar",
                ServiceFixture.serviceFile(classes(), ServiceFixture.SERVICE, content));
    }
}
