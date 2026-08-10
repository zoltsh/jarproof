package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/**
 * Where a linkage finding says it is written, which is what a source-aware report can point at.
 *
 * <p>Two breaks in one class are the interesting case: they share the artifact, the entry, and the
 * source file, and only the line tells them apart. A class compiled without debug information has to
 * answer nothing rather than something plausible, so both halves are asserted here.
 */
final class LinkageSourceTest {
    private static final String POLICY = "com/acme/lib/OrderPolicy";
    private static final String ABSENT = "com/acme/lib/Absent";
    private static final String CHECK = "check";
    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

    @TempDir
    Path workspace;

    @Test
    void namesTheSourceFileAndTheLineOfTheReferenceThatBroke() {
        List<Finding> findings = check(SourceDebugFixture.tracedCaller(LinkageFixture.CALLER, broken()));

        Finding missingMethod = EngineFixture.required(findings, "JP1003");
        assertEquals(Optional.of(SourceDebugFixture.SOURCE_FILE), missingMethod.artifact().sourceFile());
        assertEquals(Optional.of(SourceDebugFixture.lineOf(0)), missingMethod.artifact().line());

        Finding missingClass = EngineFixture.required(findings, "JP1001");
        assertEquals(Optional.of(SourceDebugFixture.SOURCE_FILE), missingClass.artifact().sourceFile());
        assertEquals(Optional.of(SourceDebugFixture.lineOf(1)), missingClass.artifact().line());
    }

    @Test
    void keepsTheClassEntryAndArtifactTheReportAlreadyNamed() {
        List<Finding> findings = check(SourceDebugFixture.tracedCaller(LinkageFixture.CALLER, broken()));

        Finding finding = EngineFixture.required(findings, "JP1003");

        assertEquals(
                Optional.of(LinkageFixture.CALLER + ArchiveLayout.CLASS_SUFFIX),
                finding.artifact().classEntry());
        assertEquals(workspace.resolve("app.jar").toString(), finding.artifact().artifact());
    }

    @Test
    void reportsTheSourceFileWithoutALineWhenNoTableCoversTheReference() {
        List<Finding> findings = check(SourceDebugFixture.untracedCaller(LinkageFixture.CALLER, broken()));

        Finding finding = EngineFixture.required(findings, "JP1003");

        assertEquals(Optional.of(SourceDebugFixture.SOURCE_FILE), finding.artifact().sourceFile());
        assertTrue(finding.artifact().line().isEmpty());
    }

    @Test
    void reportsNoSourceAtAllForAClassCompiledWithoutDebugInformation() {
        List<Finding> findings = check(LinkageFixture.caller(LinkageFixture.CALLER, broken()));

        Finding finding = EngineFixture.required(findings, "JP1003");

        assertTrue(finding.artifact().sourceFile().isEmpty());
        assertTrue(finding.artifact().line().isEmpty());
    }

    /** One reference to a method the library never declares, then one to a class nothing declares. */
    private static List<MemberReference> broken() {
        return List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, POLICY, CHECK, "()V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ABSENT, CHECK, "()V"));
    }

    private List<Finding> check(byte[] caller) {
        Path application = jar("app.jar", LinkageFixture.classes(LinkageFixture.CALLER, caller));
        Path library = jar("lib/library-1.0.jar", LinkageFixture.classes(POLICY, LinkageFixture.type(
                POLICY,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member(CHECK, "(Z)V", PUBLIC_STATIC)))));
        return LinkageFixture.check(List.of(application), List.of(library), Scope.APPLICATION);
    }

    private Path jar(String name, Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, name, entries);
    }
}
