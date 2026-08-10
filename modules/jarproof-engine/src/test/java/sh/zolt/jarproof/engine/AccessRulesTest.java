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
import sh.zolt.jarproof.api.Scope;

/** Proves JVMS 5.4.4 is modelled with nestmates, so modern bytecode is not judged by pre-11 rules. */
final class AccessRulesTest {
    private static final String LEDGER = "com/acme/nest/Ledger";
    private static final String ROW = "com/acme/nest/Ledger$Row";
    private static final String ENGINE = "com/acme/lib/Engine";
    private static final String TURBO = "com/acme/app/Turbo";
    private static final String NEIGHBOUR = "com/acme/app/Neighbour";
    private static final String REMOTE = "com/acme/lib/Neighbour";
    private static final String LOCAL_VAULT = "com/acme/app/Vault";
    private static final String BUILDER = "java/lang/AbstractStringBuilder";
    private static final int PRIVATE_STATIC = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
    private static final int PACKAGE_STATIC = Opcodes.ACC_STATIC;

    @TempDir
    Path workspace;

    @Test
    void letsANestHostReachThePrivateMembersOfItsNestedClass() {
        assertEquals(List.of(), codes(nest(List.of(ROW))));
    }

    @Test
    void reportsAPrivateMemberWhenTheHostDoesNotListTheClaimant() {
        List<Finding> findings = LinkageFixture.check(List.of(nest(List.of())), List.of(), Scope.APPLICATION);

        assertEquals(List.of("JP1007", "JP1007"), EngineFixture.codes(findings));
        assertTrue(EngineFixture.evidence(findings.get(0)).contains("the declaration in " + ROW + " is private"),
                EngineFixture.evidence(findings.get(0)).toString());
    }

    @Test
    void reportsAPrivateMemberWhenTheClaimedHostIsNotOnTheClasspath() {
        Path application = app(LinkageFixture.and(
                LinkageFixture.classes(LEDGER, LinkageFixture.nestHost(LEDGER, List.of(ROW), privateReferences())),
                ROW,
                LinkageFixture.nestMember(ROW, "com/acme/nest/Absent", privateMembers())));

        assertEquals(List.of("JP1007", "JP1007"), codes(application));
    }

    @Test
    void letsASubclassReachAProtectedMemberAcrossPackages() {
        Path application = app(LinkageFixture.classes(TURBO, LinkageFixture.caller(TURBO, ENGINE, List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, ENGINE, "tick", "()V")))));

        assertEquals(List.of(), EngineFixture.codes(
                LinkageFixture.check(List.of(application), List.of(engine()), Scope.APPLICATION)));
    }

    @Test
    void reportsAProtectedMemberReachedFromOutsideThePackageAndTheHierarchy() {
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, ENGINE, "tick", "()V")))));

        Finding finding = EngineFixture.required(
                LinkageFixture.check(List.of(application), List.of(engine()), Scope.APPLICATION), "JP1007");

        assertTrue(EngineFixture.evidence(finding).contains("the declaration in " + ENGINE + " is protected"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void letsPackagePrivateAccessStayInsideOnePackage() {
        Path application = app(LinkageFixture.and(
                LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                        LinkageFixture.CALLER,
                        List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, NEIGHBOUR, "assist", "()V")))),
                NEIGHBOUR,
                neighbour(NEIGHBOUR)));

        assertEquals(List.of(), codes(application));
    }

    @Test
    void reportsPackagePrivateAccessAcrossAPackageBoundary() {
        Path library = lib(LinkageFixture.classes(REMOTE, neighbour(REMOTE)));
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, REMOTE, "assist", "()V")))));

        Finding finding = EngineFixture.required(
                LinkageFixture.check(List.of(application), List.of(library), Scope.APPLICATION), "JP1007");

        assertTrue(EngineFixture.evidence(finding).contains("the declaration in " + REMOTE + " is package-private"),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void letsAClassReachANonPublicTypeInItsOwnPackage() {
        Path application = app(LinkageFixture.and(
                LinkageFixture.classes(
                        LinkageFixture.CALLER,
                        LinkageFixture.typeCaller(LinkageFixture.CALLER, List.of(LOCAL_VAULT))),
                LOCAL_VAULT,
                LinkageFixture.type(
                        LOCAL_VAULT, LinkageFixture.PACKAGE_CLASS, EngineFixture.OBJECT, List.of(), List.of())));

        assertEquals(List.of(), codes(application));
    }

    @Test
    void treatsANonPublicRuntimeTypeAsUnreachableWhateverThePackage() {
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(
                        ReferenceKind.INVOKE_VIRTUAL, BUILDER, "length", "()I")))));

        Finding finding = EngineFixture.required(codesAndFindings(application), "JP1006");

        assertEquals(BUILDER, finding.subject());
        assertTrue(EngineFixture.evidence(finding).contains("supplied by the target runtime from java.base"),
                EngineFixture.evidence(finding).toString());
    }

    private Path nest(List<String> nestMembers) {
        return app(LinkageFixture.and(
                LinkageFixture.classes(LEDGER, LinkageFixture.nestHost(LEDGER, nestMembers, privateReferences())),
                ROW,
                LinkageFixture.nestMember(ROW, LEDGER, privateMembers())));
    }

    private static List<MemberReference> privateReferences() {
        return List.of(
                LinkageFixture.reference(ReferenceKind.GET_FIELD, ROW, "total", "J"),
                LinkageFixture.reference(ReferenceKind.INVOKE_SPECIAL, ROW, "bump", "()V"));
    }

    private static List<MemberShape> privateMembers() {
        return List.of(
                LinkageFixture.member("total", "J", Opcodes.ACC_PRIVATE),
                LinkageFixture.member("bump", "()V", Opcodes.ACC_PRIVATE));
    }

    private static byte[] neighbour(String internalName) {
        return LinkageFixture.type(
                internalName,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("assist", "()V", PACKAGE_STATIC)));
    }

    private Path engine() {
        return lib(LinkageFixture.classes(ENGINE, LinkageFixture.type(
                ENGINE,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(
                        LinkageFixture.member("tick", "()V", Opcodes.ACC_PROTECTED),
                        LinkageFixture.member("seal", "()V", PRIVATE_STATIC)))));
    }

    private Path app(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "app.jar", entries);
    }

    private Path lib(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "lib/library-1.0.jar", entries);
    }

    private static List<String> codes(Path application) {
        return EngineFixture.codes(codesAndFindings(application));
    }

    private static List<Finding> codesAndFindings(Path application) {
        return LinkageFixture.check(List.of(application), List.of(), Scope.APPLICATION);
    }
}
