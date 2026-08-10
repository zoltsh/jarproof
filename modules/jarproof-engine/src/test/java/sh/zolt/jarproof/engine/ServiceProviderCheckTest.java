package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class ServiceProviderCheckTest {
    private static final String BASE = "com/acme/codec/AbstractCodec";
    private static final String DERIVED = "com.acme.codec.DerivedCodec";
    private static final String FILTER = "com/acme/codec/Filter";
    private static final String SUPPLIER = "java.util.function.Supplier";
    private static final String SUPPLIER_INTERNAL = "java/util/function/Supplier";

    @TempDir
    Path workspace;

    @Test
    void reportsAProviderClassNothingDeclares() {
        Path archive = providers(ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER), classes());

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4001");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.SERVICE_CONFIGURATION_ERROR, finding.predictedError());
        assertEquals(ServiceFixture.ABSENT_PROVIDER_INTERNAL, finding.subject());
        assertEquals(archive.toString(), finding.artifact().artifact());
        assertEquals(ServiceFixture.SERVICE_ENTRY, finding.artifact().classEntry().orElseThrow());
        assertTrue(EngineFixture.evidence(finding).contains("the provider is named on line 1"),
                EngineFixture.evidence(finding).toString());
        assertEquals("service provider class missing", finding.summary());
    }

    @Test
    void reportsAProviderThatDoesNotCarryTheServiceType() {
        Map<String, byte[]> classes = classes();
        classes.put("com/acme/codec/Unrelated.class", ServiceFixture.provider("com/acme/codec/Unrelated"));
        Path archive = providers(ServiceFixture.lines("com.acme.codec.Unrelated"), classes);

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4002");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.SERVICE_CONFIGURATION_ERROR, finding.predictedError());
        assertEquals("com/acme/codec/Unrelated", finding.subject());
        assertEquals("service provider does not implement the service type", finding.summary());
        assertTrue(EngineFixture.evidence(finding).contains(
                        "its superclass chain and interfaces never reach " + ServiceFixture.SERVICE),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAProviderThatImplementsADifferentInterface() {
        Map<String, byte[]> classes = classes();
        classes.put(FILTER + ".class", ServiceFixture.serviceInterface(FILTER));
        classes.put("com/acme/codec/Filtering.class",
                ServiceFixture.provider("com/acme/codec/Filtering", FILTER));
        Path archive = providers(ServiceFixture.lines("com.acme.codec.Filtering"), classes);

        List<Finding> findings = ServiceFixture.findings(archive);

        assertEquals(List.of("JP4002"), EngineFixture.codes(findings));
        assertEquals("com/acme/codec/Filtering", findings.get(0).subject());
    }

    @Test
    void reportsEveryConstructionCriterionOneProviderFails() {
        Map<String, byte[]> classes = classes();
        classes.put("com/acme/codec/Internal.class",
                ServiceFixture.packagePrivateProvider("com/acme/codec/Internal", ServiceFixture.SERVICE_INTERNAL));
        Path archive = providers(ServiceFixture.lines("com.acme.codec.Internal"), classes);

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4005");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals("service provider not instantiable", finding.summary());
        assertTrue(finding.explanation().endsWith(
                "This provider is not public, declares no public no-argument constructor."),
                finding.explanation());
        assertEquals(
                List.of("the provider is named on line 1", "the provider is not public",
                        "the provider declares no public no-argument constructor"),
                EngineFixture.evidence(finding));
    }

    @Test
    void reportsAnAbstractProviderAsUnconstructible() {
        Map<String, byte[]> classes = classes();
        classes.put(BASE + ".class", ServiceFixture.abstractProvider(BASE, ServiceFixture.SERVICE_INTERNAL));
        Path archive = providers(ServiceFixture.lines("com.acme.codec.AbstractCodec"), classes);

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4005");

        assertTrue(finding.explanation().endsWith("This provider is abstract."), finding.explanation());
        assertEquals(List.of("JP4005"), EngineFixture.codes(ServiceFixture.findings(archive)));
    }

    @Test
    void reportsAProviderWhoseOnlyConstructorIsPrivate() {
        Map<String, byte[]> classes = classes();
        classes.put("com/acme/codec/Hidden.class",
                ServiceFixture.hiddenConstructorProvider("com/acme/codec/Hidden", ServiceFixture.SERVICE_INTERNAL));
        Path archive = providers(ServiceFixture.lines("com.acme.codec.Hidden"), classes);

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4005");

        assertTrue(finding.explanation().endsWith("This provider declares no public no-argument constructor."),
                finding.explanation());
    }

    @Test
    void staysQuietAboutAProviderThatMeetsEveryRule() {
        Path archive = providers(ServiceFixture.lines(ServiceFixture.PROVIDER), classes());

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void acceptsAServiceTypeThePlatformDeclares() {
        Map<String, byte[]> classes = EngineFixture.entries(
                "com/acme/codec/Lazy.class", ServiceFixture.provider("com/acme/codec/Lazy", SUPPLIER_INTERNAL));
        Path archive = EngineFixture.jar(workspace, "lib/platform-service.jar",
                ServiceFixture.serviceFile(classes, SUPPLIER, ServiceFixture.lines("com.acme.codec.Lazy")));

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void acceptsAProviderThatInheritsTheServiceTypeFromAnIntermediateBase() {
        Map<String, byte[]> classes = classes();
        classes.put(BASE + ".class", ServiceFixture.abstractProvider(BASE, ServiceFixture.SERVICE_INTERNAL));
        classes.put("com/acme/codec/DerivedCodec.class",
                ServiceFixture.derivedProvider("com/acme/codec/DerivedCodec", BASE));
        Path archive = providers(ServiceFixture.lines(DERIVED), classes);

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void acceptsAPlatformClassRegisteredForAPlatformService() {
        Map<String, byte[]> entries = ServiceFixture.serviceFile(
                EngineFixture.entries(), "java.lang.Runnable", ServiceFixture.lines("java.lang.Thread"));
        Path archive = EngineFixture.jar(workspace, "lib/platform-provider.jar", entries);

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void resolvesAProviderFromThePlatformEvenWhenAnArtifactShadowsIt() {
        Map<String, byte[]> classes = classes();
        classes.put(SUPPLIER_INTERNAL + ".class", ServiceFixture.provider(SUPPLIER_INTERNAL));
        Path archive = providers(ServiceFixture.lines(SUPPLIER), classes);

        Finding finding = EngineFixture.required(ServiceFixture.findings(archive), "JP4005");

        assertTrue(finding.explanation().endsWith("This provider is an interface rather than a class."),
                finding.explanation());
        assertEquals(SUPPLIER_INTERNAL, finding.subject());
    }

    @Test
    void skipsTheServiceTypeQuestionWhenNothingDeclaresTheServiceType() {
        Map<String, byte[]> classes = EngineFixture.entries(
                ServiceFixture.PROVIDER_INTERNAL + ".class",
                ServiceFixture.provider(ServiceFixture.PROVIDER_INTERNAL));
        Path archive = EngineFixture.jar(workspace, "lib/absent-service.jar", ServiceFixture.serviceFile(
                classes, "com.acme.absent.Codec",
                ServiceFixture.lines(ServiceFixture.PROVIDER, ServiceFixture.ABSENT_PROVIDER)));

        List<Finding> findings = ServiceFixture.findings(archive);

        assertEquals(List.of("JP4001"), EngineFixture.codes(findings));
        assertEquals(ServiceFixture.ABSENT_PROVIDER_INTERNAL, findings.get(0).subject());
    }

    @Test
    void skipsTheServiceTypeQuestionWhenAProviderSupertypeIsDeclaredNowhere() {
        Map<String, byte[]> classes = classes();
        classes.put("com/acme/codec/DerivedCodec.class",
                ServiceFixture.derivedProvider("com/acme/codec/DerivedCodec", "com/acme/absent/Base"));
        Path archive = providers(ServiceFixture.lines(DERIVED), classes);

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void skipsAProviderWhoseClassFileNoParserAccepts() {
        Map<String, byte[]> classes = classes();
        classes.put("com/acme/codec/Future.class", EngineFixture.withVersion(
                ServiceFixture.provider("com/acme/codec/Future"), 200, 0));
        Path archive = providers(ServiceFixture.lines("com.acme.codec.Future"), classes);

        assertEquals(List.of(), ServiceFixture.findings(archive));
    }

    @Test
    void collapsesIdenticalFindingsRepeatedLinesProduce() {
        Path archive = providers(
                ServiceFixture.lines(ServiceFixture.ABSENT_PROVIDER, ServiceFixture.ABSENT_PROVIDER), classes());

        List<Finding> findings = ServiceFixture.findings(archive);

        assertEquals(List.of("JP4001", "JP4004"), EngineFixture.codes(findings));
        assertTrue(EngineFixture.evidence(findings.get(0)).contains("the provider is named on line 1"),
                EngineFixture.evidence(findings.get(0)).toString());
    }

    private Map<String, byte[]> classes() {
        Map<String, byte[]> entries = EngineFixture.entries(
                ServiceFixture.SERVICE_INTERNAL + ".class",
                ServiceFixture.serviceInterface(ServiceFixture.SERVICE_INTERNAL));
        entries.put(ServiceFixture.PROVIDER_INTERNAL + ".class",
                ServiceFixture.provider(ServiceFixture.PROVIDER_INTERNAL, ServiceFixture.SERVICE_INTERNAL));
        return entries;
    }

    private Path providers(String content, Map<String, byte[]> classes) {
        return EngineFixture.jar(workspace, "lib/providers.jar",
                ServiceFixture.serviceFile(classes, ServiceFixture.SERVICE, content));
    }
}
