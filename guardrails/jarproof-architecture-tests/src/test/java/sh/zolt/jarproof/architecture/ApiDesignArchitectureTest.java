package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

final class ApiDesignArchitectureTest {
    private static final Set<String> APPROVED_PUBLIC_TYPES = Set.of(
            "sh.zolt.jarproof.api.ArtifactLocation",
            "sh.zolt.jarproof.api.Evidence",
            "sh.zolt.jarproof.api.Finding",
            "sh.zolt.jarproof.api.FindingCode",
            "sh.zolt.jarproof.api.PredictedError",
            "sh.zolt.jarproof.api.PreviewMode",
            "sh.zolt.jarproof.api.Remediation",
            "sh.zolt.jarproof.api.Scope",
            "sh.zolt.jarproof.api.Severity",
            "sh.zolt.jarproof.api.TargetRuntime",
            "sh.zolt.jarproof.api.VerificationRequest",
            "sh.zolt.jarproof.api.VerificationResult",
            "sh.zolt.jarproof.cli.Main",
            "sh.zolt.jarproof.engine.Jarproof");
    private static final List<Class<?>> API_TYPES = List.of(
            ArtifactLocation.class,
            Evidence.class,
            Finding.class,
            FindingCode.class,
            PredictedError.class,
            PreviewMode.class,
            Remediation.class,
            Scope.class,
            Severity.class,
            TargetRuntime.class,
            VerificationRequest.class,
            VerificationResult.class);
    private static final Set<String> APPROVED_COLLECTION_RETURNS = Set.of(
            "sh.zolt.jarproof.api.Finding.evidence",
            "sh.zolt.jarproof.api.Finding.remediation",
            "sh.zolt.jarproof.api.VerificationRequest.applications",
            "sh.zolt.jarproof.api.VerificationRequest.classpath",
            "sh.zolt.jarproof.api.VerificationResult.findings");
    private static final Pattern AMBIGUOUS_DOMAIN_STATE = Pattern.compile(
            "\\b(?:String|boolean|Boolean)\\s+"
                    + "(?:status|state|mode|kind|type|format|scope|severity|previewEnabled|predictedError)\\b");

    @Test
    void everyPublicProductionTypeIsAnIntentionalEntrypoint() {
        Set<String> actual = JavaSourceParser.productionSources().stream()
                .flatMap(source -> source.types().stream())
                .filter(type -> type.modifiers().contains(javax.lang.model.element.Modifier.PUBLIC))
                .map(JavaTypeShape::qualifiedName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertTrue(actual.equals(APPROVED_PUBLIC_TYPES),
                () -> "Update the deliberate public API allowlist. expected=" + APPROVED_PUBLIC_TYPES + ", actual=" + actual);
    }

    @Test
    void publicApiDoesNotAcceptAmbiguousBooleanArguments() {
        for (Class<?> type : API_TYPES) {
            for (Executable executable : publicExecutables(type)) {
                for (Class<?> parameter : executable.getParameterTypes()) {
                    assertFalse(parameter == boolean.class || parameter == Boolean.class, describe(executable));
                }
            }
        }
    }

    @Test
    void publicApiUsesImmutableSurfaceTypes() {
        for (Class<?> type : API_TYPES) {
            assertTrue(type.isRecord() || type.isEnum() || type.isInterface() || Modifier.isFinal(type.getModifiers()), type.getName());
            for (Field field : type.getFields()) {
                assertTrue(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), describe(field));
                assertSafeType(field.getType(), field);
            }
            for (Method method : type.getMethods()) {
                if (method.getDeclaringClass() == type) {
                    assertSafeType(method.getReturnType(), method);
                    if (Collection.class.isAssignableFrom(method.getReturnType())) {
                        assertTrue(APPROVED_COLLECTION_RETURNS.contains(describe(method)),
                                describe(method) + " needs an immutability contract and focused test");
                    }
                    for (Class<?> parameter : method.getParameterTypes()) {
                        assertSafeType(parameter, method);
                    }
                }
            }
            for (Executable constructor : type.getConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    assertSafeType(parameter, constructor);
                }
            }
        }
    }

    @Test
    void closedDomainVocabularyIsNotStoredAsRawTextOrBooleanState() {
        for (java.nio.file.Path source : RepositoryLayout.productionJavaFiles("modules/jarproof-api")) {
            assertFalse(
                    AMBIGUOUS_DOMAIN_STATE.matcher(RepositoryLayout.text(source)).find(),
                    RepositoryLayout.relative(source));
        }
    }

    private static List<Executable> publicExecutables(Class<?> type) {
        java.util.ArrayList<Executable> executables = new java.util.ArrayList<>();
        executables.addAll(List.of(type.getConstructors()));
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == type) {
                executables.add(method);
            }
        }
        return List.copyOf(executables);
    }

    private static void assertSafeType(Class<?> type, Member member) {
        boolean generatedEnumValues = member instanceof Method method
                && method.getDeclaringClass().isEnum()
                && method.getName().equals("values");
        assertFalse(type.isArray() && !generatedEnumValues, describe(member) + " exposes an array");
        boolean concreteMutableCollection = Collection.class.isAssignableFrom(type) && !type.isInterface();
        assertFalse(concreteMutableCollection, describe(member) + " exposes " + type.getName());
    }

    private static String describe(Member member) {
        return member.getDeclaringClass().getName() + "." + member.getName();
    }
}
