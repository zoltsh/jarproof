package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.Severity;

/** Proves DESIGN section 4: origin decides what is analysed, and what a finding costs the reader. */
final class LinkageScopeTest {
    private static final String ABSENT = "com/acme/lib/Absent";
    private static final String WORKER = "com/acme/lib/Worker";
    private static final String SECOND = "com/acme/app/Second";

    @TempDir
    Path workspace;

    @Test
    void analysesOnlyApplicationBytecodeUnderTheApplicationScope() {
        List<Finding> findings = check(Scope.APPLICATION);

        assertEquals(List.of("JP1001"), EngineFixture.codes(findings));
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertEquals(LinkageFixture.CALLER + ArchiveLayout.CLASS_SUFFIX,
                findings.get(0).artifact().classEntry().orElseThrow());
    }

    @Test
    void analysesEveryOriginUnderTheAllScopeAndDowngradesLibraryEvidence() {
        List<Finding> findings = check(Scope.ALL);

        assertEquals(List.of("JP1001", "JP1001"), EngineFixture.codes(findings));
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertEquals(Severity.WARNING, findings.get(1).severity());
        assertEquals(WORKER + ArchiveLayout.CLASS_SUFFIX, findings.get(1).artifact().classEntry().orElseThrow());
    }

    @Test
    void reportsOneFindingForRepeatedCallSitesAndKeepsTheFirstEvidence() {
        Path application = app(LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.repeatedCaller(
                LinkageFixture.CALLER,
                LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ABSENT, "reset", "()V"))));

        List<Finding> findings = LinkageFixture.check(List.of(application), List.of(), Scope.APPLICATION);

        assertEquals(List.of("JP1001"), EngineFixture.codes(findings));
        assertTrue(EngineFixture.evidence(findings.get(0)).contains("referenced from " + LinkageFixture.RUN),
                EngineFixture.evidence(findings.get(0)).toString());
        assertTrue(EngineFixture.evidence(findings.get(0)).stream()
                        .noneMatch(line -> line.contains(LinkageFixture.AGAIN)),
                EngineFixture.evidence(findings.get(0)).toString());
    }

    @Test
    void keepsTheSameBreakApartForDifferentReferencingClasses() {
        Path application = app(LinkageFixture.and(
                LinkageFixture.classes(LinkageFixture.CALLER, LinkageFixture.caller(
                        LinkageFixture.CALLER, List.of(missing()))),
                SECOND,
                LinkageFixture.caller(SECOND, List.of(missing()))));

        List<Finding> findings = LinkageFixture.check(List.of(application), List.of(), Scope.APPLICATION);

        assertEquals(List.of("JP1001", "JP1001"), EngineFixture.codes(findings));
        assertEquals(2, findings.stream().map(finding -> finding.artifact().classEntry()).distinct().count());
    }

    private List<Finding> check(Scope scope) {
        Path application = app(LinkageFixture.classes(
                LinkageFixture.CALLER, LinkageFixture.caller(LinkageFixture.CALLER, List.of(missing()))));
        Path library = EngineFixture.jar(workspace, "lib/library-1.0.jar",
                LinkageFixture.classes(WORKER, LinkageFixture.caller(WORKER, List.of(missing()))));
        return LinkageFixture.check(List.of(application), List.of(library), scope);
    }

    private static MemberReference missing() {
        return LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ABSENT, "reset", "()V");
    }

    private Path app(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "app.jar", entries);
    }
}
