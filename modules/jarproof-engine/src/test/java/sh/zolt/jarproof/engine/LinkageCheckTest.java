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
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;

final class LinkageCheckTest {
    private static final String ABSENT = "com/acme/lib/Absent";
    private static final String CHECKS = "com/acme/lib/Checks";
    private static final String CLOCK = "com/acme/lib/Clock";
    private static final String BUDGET = "com/acme/lib/Budget";
    private static final String RENDERER = "com/acme/lib/Renderer";
    private static final String GREETER = "com/acme/lib/Greeter";
    private static final String VAULT = "com/acme/lib/Vault";
    private static final String SHARED = "com/acme/app/Shared";
    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

    @TempDir
    Path workspace;

    @Test
    void reportsAClassNeitherTheClasspathNorTheRuntimeDeclares() {
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ABSENT, "reset", "()V")))));

        Finding finding = EngineFixture.required(check(application, List.of()), "JP1001");

        assertEquals(PredictedError.NO_CLASS_DEF_FOUND_ERROR, finding.predictedError());
        assertEquals(ABSENT, finding.subject());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(application.toString(), finding.artifact().artifact());
        assertEquals(LinkageFixture.CALLER + ArchiveLayout.CLASS_SUFFIX, finding.artifact().classEntry().orElseThrow());
        assertTrue(EngineFixture.evidence(finding).contains("referenced from " + LinkageFixture.RUN),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAClassNamedByATypeInstructionThatResolvesToNothing() {
        Path application = app(LinkageFixture.classes(
                LinkageFixture.CALLER, LinkageFixture.typeCaller(LinkageFixture.CALLER, List.of(ABSENT))));

        Finding finding = EngineFixture.required(check(application, List.of()), "JP1001");

        assertEquals(ABSENT, finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains("no artifact on the effective classpath and no module"
                        + " of the target runtime declares " + ABSENT),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAFieldTheResolvedClassNeverDeclares() {
        Path library = lib(LinkageFixture.classes(BUDGET, LinkageFixture.type(
                BUDGET,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("retries", "I", PUBLIC_STATIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.GET_STATIC, BUDGET, "limit", "I")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1002");

        assertEquals(PredictedError.NO_SUCH_FIELD_ERROR, finding.predictedError());
        assertEquals(BUDGET + "#limitI", finding.subject());
        assertTrue(EngineFixture.evidence(finding).get(0).startsWith("selected " + library),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAMethodTheResolvedClassNeverDeclaresAndNamesTheDescriptorsItDoes() {
        Path library = lib(LinkageFixture.classes(CHECKS, LinkageFixture.type(
                CHECKS,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(
                        LinkageFixture.member("checkArgument", "(Z)V", PUBLIC_STATIC),
                        LinkageFixture.member("checkArgument", "(ZLjava/lang/String;)V", PUBLIC_STATIC),
                        LinkageFixture.member("checkArgument", "(ZLjava/lang/Object;)V", PUBLIC_STATIC),
                        LinkageFixture.member("checkArgument", "(ZJ)V", PUBLIC_STATIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, CHECKS, "checkArgument",
                        "(ZLjava/lang/String;Ljava/lang/Object;)V")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1003");
        List<String> evidence = EngineFixture.evidence(finding);

        assertEquals(PredictedError.NO_SUCH_METHOD_ERROR, finding.predictedError());
        assertEquals(CHECKS + "#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V", finding.subject());
        assertEquals(3, evidence.stream().filter(line -> line.contains("also declares")).count(), evidence.toString());
        assertTrue(evidence.contains("the resolved class also declares checkArgument(ZLjava/lang/String;)V"),
                evidence.toString());
    }

    @Test
    void resolvesASelfReferenceThroughTheWinningCopyOfItsOwnClass() {
        Path winner = EngineFixture.jar(workspace, "app-core.jar", LinkageFixture.classes(
                SHARED,
                LinkageFixture.type(SHARED, LinkageFixture.PUBLIC_CLASS, EngineFixture.OBJECT, List.of(), List.of())));
        Path shadowed = EngineFixture.jar(workspace, "app-web.jar", LinkageFixture.and(
                LinkageFixture.classes(SHARED, LinkageFixture.type(
                        SHARED,
                        LinkageFixture.PUBLIC_CLASS,
                        EngineFixture.OBJECT,
                        List.of(),
                        List.of(LinkageFixture.member("added", "()V", PUBLIC_STATIC)))),
                LinkageFixture.CALLER,
                LinkageFixture.caller(LinkageFixture.CALLER, List.of(
                        LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, SHARED, "added", "()V")))));

        Finding finding = EngineFixture.required(
                LinkageFixture.check(List.of(winner, shadowed), List.of(), Scope.APPLICATION), "JP1003");

        assertEquals(SHARED + "#added()V", finding.subject());
        assertTrue(EngineFixture.evidence(finding).get(0).startsWith("selected " + winner),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAnInstanceMethodReachedAsAStaticOne() {
        Path library = lib(LinkageFixture.classes(CLOCK, LinkageFixture.type(
                CLOCK,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("now", "()J", Opcodes.ACC_PUBLIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, CLOCK, "now", "()J")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1004");

        assertEquals(PredictedError.INCOMPATIBLE_CLASS_CHANGE_ERROR, finding.predictedError());
        assertEquals(CLOCK + "#now()J", finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains("the declaration in " + CLOCK + " is an instance member"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAStaticFieldReachedThroughAnInstance() {
        Path library = lib(LinkageFixture.classes(BUDGET, LinkageFixture.type(
                BUDGET,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("retries", "I", PUBLIC_STATIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.GET_FIELD, BUDGET, "retries", "I")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1004");

        assertEquals(BUDGET + "#retriesI", finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains("the declaration in " + BUDGET + " is static"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAnInterfaceInvocationThatResolvesToAClass() {
        Path library = lib(LinkageFixture.classes(RENDERER, LinkageFixture.type(
                RENDERER,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("render", "()V", Opcodes.ACC_PUBLIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_INTERFACE, RENDERER, "render", "()V")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1005");

        assertEquals(PredictedError.INCOMPATIBLE_CLASS_CHANGE_ERROR, finding.predictedError());
        assertEquals(RENDERER + "#render()V", finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains("the resolved type is a class"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAVirtualInvocationThatResolvesToAnInterface() {
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, GREETER, "greet", "()V")))));

        Finding finding = EngineFixture.required(check(application, List.of(greeter())), "JP1005");

        assertTrue(EngineFixture.evidence(finding).contains("the resolved type is an interface"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAnInterfaceCallThatOnlyAProtectedObjectMethodCouldSatisfy() {
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(
                        ReferenceKind.INVOKE_INTERFACE, GREETER, "clone", "()Ljava/lang/Object;")))));

        Finding finding = EngineFixture.required(check(application, List.of(greeter())), "JP1003");

        assertEquals(GREETER + "#clone()Ljava/lang/Object;", finding.subject());
    }

    @Test
    void reportsANonPublicClassReachedFromAnotherPackage() {
        Path library = lib(LinkageFixture.classes(VAULT, LinkageFixture.type(
                VAULT,
                LinkageFixture.PACKAGE_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("open", "()V", PUBLIC_STATIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, VAULT, "open", "()V")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1006");

        assertEquals(PredictedError.ILLEGAL_ACCESS_ERROR, finding.predictedError());
        assertEquals(VAULT, finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains(
                        "it is not public, and " + LinkageFixture.CALLER + " is outside its run-time package"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsANonPublicClassNamedByATypeInstruction() {
        Path library = lib(LinkageFixture.classes(VAULT, LinkageFixture.type(
                VAULT, LinkageFixture.PACKAGE_CLASS, EngineFixture.OBJECT, List.of(), List.of())));
        Path application = app(LinkageFixture.classes(
                LinkageFixture.CALLER, LinkageFixture.typeCaller(LinkageFixture.CALLER, List.of(VAULT))));

        assertEquals(List.of("JP1006"), EngineFixture.codes(check(application, List.of(library))));
    }

    @Test
    void reportsAPrivateMemberReachedFromOutsideTheNest() {
        Path library = lib(LinkageFixture.classes(VAULT, LinkageFixture.type(
                VAULT,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("secret", "()V", Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)))));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, VAULT, "secret", "()V")))));

        Finding finding = EngineFixture.required(check(application, List.of(library)), "JP1007");

        assertEquals(PredictedError.ILLEGAL_ACCESS_ERROR, finding.predictedError());
        assertEquals(VAULT + "#secret()V", finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains("the declaration in " + VAULT + " is private"),
                EngineFixture.evidence(finding).toString());
    }

    private Path greeter() {
        return lib(LinkageFixture.classes(GREETER, LinkageFixture.type(
                GREETER,
                LinkageFixture.PUBLIC_INTERFACE,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("greet", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)))));
    }

    private Path app(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "app.jar", entries);
    }

    private Path lib(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "lib/library-1.0.jar", entries);
    }

    private static List<Finding> check(Path application, List<Path> classpath) {
        return LinkageFixture.check(List.of(application), classpath, Scope.APPLICATION);
    }
}
