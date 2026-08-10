package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Severity;

final class DuplicateClassCheckTest {
    private static final String DUPLICATE = "com/acme/dup/Duplicate";
    private static final String DUPLICATE_ENTRY = "com/acme/dup/Duplicate.class";

    @TempDir
    Path workspace;

    @Test
    void reportsIdenticalCopiesAsInformationOnly() {
        Path first = EngineFixture.jar(workspace, "lib/first.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        Path second = EngineFixture.jar(workspace, "lib/second.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));

        Finding finding = EngineFixture.required(
                EngineFixture.verify(List.of(first), List.of(second), 17), "JP2001");

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(PredictedError.NONE, finding.predictedError());
        assertEquals(DUPLICATE, finding.subject());
        assertEquals(first.toString(), finding.artifact().artifact());
        assertEquals(Optional.of(DUPLICATE_ENTRY), finding.artifact().classEntry());
        assertEquals(2, finding.evidence().size());
    }

    @Test
    void exemptsModuleDescriptorsSharedByModularArtifacts() {
        Path first = EngineFixture.jar(workspace, "lib/alpha-module.jar",
                EngineFixture.entries("module-info.class",
                        EngineFixture.classFile(ModuleClaimReader.DESCRIPTOR_NAME)));
        Path second = EngineFixture.jar(workspace, "lib/beta-module.jar",
                EngineFixture.entries("module-info.class",
                        EngineFixture.classFileWithField(ModuleClaimReader.DESCRIPTOR_NAME, "extra")));

        List<Finding> findings = EngineFixture.verify(List.of(first), List.of(second), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2001"));
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2002"));
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2006"));
    }

    @Test
    void exemptsVersionedModuleDescriptorsLikeBaseOnes() {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        Map<String, byte[]> versioned = EngineFixture.entries(
                "module-info.class", EngineFixture.classFile(ModuleClaimReader.DESCRIPTOR_NAME));
        versioned.put("META-INF/versions/9/module-info.class",
                EngineFixture.classFileWithField(ModuleClaimReader.DESCRIPTOR_NAME, "extra"));
        Path modern = EngineFixture.jar(workspace, "lib/modern.jar",
                EngineFixture.withManifest(versioned, manifest));
        Path plain = EngineFixture.jar(workspace, "lib/plain.jar",
                EngineFixture.entries("module-info.class",
                        EngineFixture.classFile(ModuleClaimReader.DESCRIPTOR_NAME)));

        List<Finding> findings = EngineFixture.verify(List.of(modern), List.of(plain), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2001"));
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2002"));
    }

    @Test
    void reportsDifferingCopiesAsAWarningAgainstTheWinner() {
        Path winner = EngineFixture.jar(workspace, "lib/winner.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        Path loser = EngineFixture.jar(workspace, "lib/loser.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFileWithField(DUPLICATE, "extra")));

        List<Finding> findings = EngineFixture.verify(List.of(winner), List.of(loser), 17);
        Finding finding = EngineFixture.required(findings, "JP2002");

        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(winner.toString(), finding.artifact().artifact());
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2006"));
        assertTrue(EngineFixture.evidence(finding).stream()
                .allMatch(detail -> detail.matches(".* declares .* with digest [0-9a-f]{8}")),
                EngineFixture.evidence(finding).toString());
    }

    @Test
    void reportsAnUnpredictableWinnerWhenOneWildcardProducedBothCopies() {
        EngineFixture.jar(workspace, "lib/alpha.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        EngineFixture.jar(workspace, "lib/beta.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFileWithField(DUPLICATE, "extra")));
        Path wildcard = workspace.resolve("lib").resolve("*");

        List<Finding> findings = EngineFixture.verify(List.of(application()), List.of(wildcard), 17);
        Finding finding = EngineFixture.required(findings, "JP2006");

        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(workspace.resolve("lib/alpha.jar").toString(), finding.artifact().artifact());
        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2002"));
        assertTrue(finding.explanation().contains("either copy"), finding.explanation());
        assertTrue(EngineFixture.evidence(finding).stream()
                .anyMatch(detail -> detail.contains(wildcard.toString())), EngineFixture.evidence(finding).toString());
    }

    @Test
    void leavesAWildcardWinnerAloneWhenTheCopiesAreIdentical() {
        EngineFixture.jar(workspace, "lib/alpha.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        EngineFixture.jar(workspace, "lib/beta.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        Path wildcard = workspace.resolve("lib").resolve("*");

        List<Finding> findings = EngineFixture.verify(List.of(application()), List.of(wildcard), 17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2006"));
        assertEquals(Severity.INFO, EngineFixture.required(findings, "JP2001").severity());
    }

    @Test
    void looksPastASameWildcardCopyThatMatchesTheWinner() {
        EngineFixture.jar(workspace, "lib/alpha.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        EngineFixture.jar(workspace, "lib/beta.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        EngineFixture.jar(workspace, "lib/gamma.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFileWithField(DUPLICATE, "extra")));
        Path wildcard = workspace.resolve("lib").resolve("*");

        List<Finding> findings = EngineFixture.verify(List.of(application()), List.of(wildcard), 17);

        assertEquals(3, EngineFixture.required(findings, "JP2006").evidence().size() - 1);
    }

    @Test
    void treatsSeparateWildcardsAsAnOrderTheCallerChose() {
        EngineFixture.jar(workspace, "left/shared.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFile(DUPLICATE)));
        EngineFixture.jar(workspace, "right/shared.jar",
                EngineFixture.entries(DUPLICATE_ENTRY, EngineFixture.classFileWithField(DUPLICATE, "extra")));

        List<Finding> findings = EngineFixture.verify(
                List.of(application()),
                List.of(workspace.resolve("left").resolve("*"), workspace.resolve("right").resolve("*")),
                17);

        assertEquals(Optional.empty(), EngineFixture.coded(findings, "JP2006"));
        assertEquals(workspace.resolve("left/shared.jar").toString(),
                EngineFixture.required(findings, "JP2002").artifact().artifact());
    }

    private Path application() {
        return EngineFixture.jar(workspace, "app.jar",
                EngineFixture.entries("com/acme/App.class", EngineFixture.classFile("com/acme/App")));
    }
}
