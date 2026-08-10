package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class ModuleProviderCheckTest {
    private static final String ARCHIVE = "lib/orders.jar";
    private static final String LIBRARY = "lib/codec.jar";
    private static final String SERVICE = ServiceFixture.SERVICE_INTERNAL;
    private static final String PROVIDER = ServiceFixture.PROVIDER_INTERNAL;
    private static final String UNRELATED = "com/acme/codec/Unrelated";
    private static final String ABSTRACT = "com/acme/codec/AbstractCodec";
    private static final String DERIVED = "com/acme/codec/DerivedCodec";
    private static final String HIDDEN = "com/acme/codec/Hidden";
    private static final String INTERNAL = "com/acme/codec/Internal";
    private static final String CLASS_SUFFIX = ArchiveLayout.CLASS_SUFFIX;
    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

    @TempDir
    Path workspace;

    @Test
    void reportsAProviderTheModuleDoesNotDeclare() {
        Path archive = provides(SERVICE, ServiceFixture.ABSENT_PROVIDER_INTERNAL, classes());

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5003");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(PredictedError.SERVICE_CONFIGURATION_ERROR, finding.predictedError());
        assertEquals(ServiceFixture.ABSENT_PROVIDER_INTERNAL, finding.subject());
        assertEquals(ModuleFixture.DESCRIPTOR_ENTRY, finding.artifact().classEntry().orElseThrow());
        assertEquals("module service provider unusable", finding.summary());
        assertEquals(
                List.of("the provides clause of module " + ModuleFixture.MODULE
                                + " registers it for the service " + SERVICE,
                        "the provider class is not declared by the artifact whose descriptor registers it"),
                EngineFixture.evidence(finding));
    }

    @Test
    void reportsAProviderThatDoesNotCarryTheServiceType() {
        Map<String, byte[]> classes = classes();
        classes.put(UNRELATED + CLASS_SUFFIX, ServiceFixture.provider(UNRELATED));
        Path archive = provides(SERVICE, UNRELATED, classes);

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5003");

        assertTrue(EngineFixture.evidence(finding).contains("the provider class reaches the service type"
                        + " through neither its superclass chain nor its interfaces"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAnAbstractProvider() {
        Map<String, byte[]> classes = classes();
        classes.put(ABSTRACT + CLASS_SUFFIX, ServiceFixture.abstractProvider(ABSTRACT, SERVICE));
        Path archive = provides(SERVICE, ABSTRACT, classes);

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5003");

        assertEquals(List.of("the provider class is declared abstract"), criteria(finding));
    }

    @Test
    void reportsANonPublicProviderAndItsMissingEntryPoint() {
        Map<String, byte[]> classes = classes();
        classes.put(INTERNAL + CLASS_SUFFIX, ServiceFixture.packagePrivateProvider(INTERNAL, SERVICE));
        Path archive = provides(SERVICE, INTERNAL, classes);

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5003");

        assertEquals(
                List.of("the provider class is not declared public",
                        "the provider class declares neither a public no-argument constructor nor a public"
                                + " static provider method"),
                criteria(finding));
    }

    @Test
    void reportsAnInterfaceProviderOnlyAsAnInterface() {
        Map<String, byte[]> classes = classes();
        Path archive = provides(SERVICE, SERVICE, classes);

        Finding finding = EngineFixture.required(ModuleFixture.findings(archive), "JP5003");

        assertEquals(
                List.of("the provider class is an interface, and only a class can be constructed"),
                criteria(finding));
    }

    @Test
    void reportsAProviderWhoseOnlyConstructorIsPrivate() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX, ServiceFixture.hiddenConstructorProvider(HIDDEN, SERVICE));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(1, criteria(EngineFixture.required(ModuleFixture.findings(archive), "JP5003")).size());
    }

    @Test
    void acceptsAProviderWithAPublicNoArgumentConstructor() {
        Map<String, byte[]> classes = classes();
        classes.put(PROVIDER + CLASS_SUFFIX, ServiceFixture.provider(PROVIDER, SERVICE));

        assertEquals(List.of(), ModuleFixture.findings(provides(SERVICE, PROVIDER, classes)));
    }

    @Test
    void acceptsAStaticFactoryProviderWithNoUsableConstructor() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX, ModuleFixture.factoryProvider(HIDDEN, SERVICE, PUBLIC_STATIC, SERVICE));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void rejectsAFactoryWhoseReturnedTypeIsNotTheService() {
        Map<String, byte[]> classes = classes();
        classes.put(UNRELATED + CLASS_SUFFIX, ServiceFixture.provider(UNRELATED));
        classes.put(HIDDEN + CLASS_SUFFIX, ModuleFixture.factoryProvider(HIDDEN, SERVICE, PUBLIC_STATIC, UNRELATED));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(1, criteria(EngineFixture.required(ModuleFixture.findings(archive), "JP5003")).size());
    }

    @Test
    void acceptsAFactoryWhoseReturnedTypeNothingDeclares() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX,
                ModuleFixture.factoryProvider(HIDDEN, SERVICE, PUBLIC_STATIC, "com/acme/absent/Codec"));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void rejectsAFactoryThatIsNotPublic() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX,
                ModuleFixture.factoryProvider(HIDDEN, SERVICE, Opcodes.ACC_STATIC, SERVICE));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of("JP5003"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void rejectsAFactoryThatIsAnInstanceMethod() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX, ModuleFixture.instanceFactoryProvider(HIDDEN, SERVICE));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of("JP5003"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void rejectsAFactoryThatTakesAnArgument() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX, ModuleFixture.shapedFactoryProvider(
                HIDDEN, SERVICE, "(Ljava/lang/String;)L" + SERVICE + ";"));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of("JP5003"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void rejectsAFactoryThatReturnsNothing() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX, ModuleFixture.shapedFactoryProvider(HIDDEN, SERVICE, "()V"));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of("JP5003"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void rejectsAFieldNamedLikeAFactory() {
        Map<String, byte[]> classes = classes();
        classes.put(HIDDEN + CLASS_SUFFIX, ModuleFixture.factoryFieldProvider(HIDDEN, SERVICE));
        Path archive = provides(SERVICE, HIDDEN, classes);

        assertEquals(List.of("JP5003"), EngineFixture.codes(ModuleFixture.findings(archive)));
    }

    @Test
    void fallsBackToTheConstructorWhenTheFactoryDoesNotFit() {
        Map<String, byte[]> classes = classes();
        classes.put(UNRELATED + CLASS_SUFFIX, ServiceFixture.provider(UNRELATED));
        classes.put(PROVIDER + CLASS_SUFFIX,
                ModuleFixture.constructedFactoryProvider(PROVIDER, SERVICE, UNRELATED));
        Path archive = provides(SERVICE, PROVIDER, classes);

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void skipsAProviderWhoseClassFileNoParserAccepts() {
        Map<String, byte[]> classes = classes();
        classes.put(PROVIDER + CLASS_SUFFIX,
                EngineFixture.withVersion(ServiceFixture.provider(PROVIDER, SERVICE), 200, 0));
        Path archive = provides(SERVICE, PROVIDER, classes);

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void staysQuietAboutTheServiceTypeWhenNothingDeclaresIt() {
        Map<String, byte[]> classes = EngineFixture.entries(
                PROVIDER + CLASS_SUFFIX, ServiceFixture.provider(PROVIDER));
        Path archive = provides("com/acme/absent/Codec", PROVIDER, classes);

        assertEquals(List.of(), ModuleFixture.findings(archive));
    }

    @Test
    void reportsEveryFaultOfOneProviderInOneFinding() {
        Map<String, byte[]> classes = classes();
        classes.put(ABSTRACT + CLASS_SUFFIX, ServiceFixture.abstractProvider(ABSTRACT));
        Path archive = provides(SERVICE, ABSTRACT, classes);

        List<Finding> findings = ModuleFixture.findings(archive);

        assertEquals(List.of("JP5003"), EngineFixture.codes(findings));
        assertEquals(2, criteria(findings.get(0)).size());
    }

    @Test
    void acceptsAProviderThatInheritsTheServiceFromAnotherArtifact() {
        Map<String, byte[]> library = classes();
        library.put(ABSTRACT + CLASS_SUFFIX, ServiceFixture.abstractProvider(ABSTRACT, SERVICE));
        Path other = EngineFixture.jar(workspace, LIBRARY, library);
        Map<String, byte[]> classes = EngineFixture.entries(
                DERIVED + CLASS_SUFFIX, ServiceFixture.derivedProvider(DERIVED, ABSTRACT));
        Path archive = provides(SERVICE, DERIVED, classes);

        assertEquals(List.of(), ModuleFixture.findings(List.of(archive), List.of(other)));
    }

    private static List<String> criteria(Finding finding) {
        return EngineFixture.evidence(finding).stream().skip(1).toList();
    }

    private Map<String, byte[]> classes() {
        return EngineFixture.entries(SERVICE + CLASS_SUFFIX, ServiceFixture.serviceInterface(SERVICE));
    }

    private Path provides(String service, String provider, Map<String, byte[]> classes) {
        return EngineFixture.jar(workspace, ARCHIVE, ModuleFixture.withDescriptor(classes,
                ModuleFixture.descriptor(
                        ModuleFixture.MODULE, module -> module.visitProvide(service, provider))));
    }
}
