package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReferenceIndexTest {
    @TempDir
    Path workspace;

    @Test
    void capturesTheDeclaredShapeOfAClass() {
        ClassShape shape = indexed().shape().orElseThrow();

        assertEquals(ReferenceFixture.OWNER, shape.internalName());
        assertEquals(Optional.of(EngineFixture.OBJECT), shape.superInternalName());
        assertEquals(List.of("java/lang/Runnable"), shape.interfaceInternalNames());
        assertEquals(Optional.of(ReferenceFixture.NEST_HOST), shape.nestHostInternalName());
        assertEquals(List.of(ReferenceFixture.NEST_MEMBER), shape.nestMemberInternalNames());
    }

    @Test
    void capturesEveryDeclaredMember() {
        List<MemberShape> members = indexed().shape().orElseThrow().members();

        assertTrue(members.contains(new MemberShape("count", "I", 2)), members.toString());
        assertTrue(members.stream().anyMatch(member -> member.name().equals("run")
                && member.descriptor().equals("()V")), members.toString());
    }

    @Test
    void capturesTypesNamedByInstructionsAndConstants() {
        List<String> types = indexed().references().types().stream().map(TypeReference::internalName).toList();

        assertTrue(types.contains(ReferenceFixture.TARGET), types.toString());
        assertTrue(types.contains(ReferenceFixture.CATCH_TYPE), types.toString());
        assertTrue(types.contains(ReferenceFixture.ARRAY_TARGET), types.toString());
        assertTrue(types.contains(ReferenceFixture.MULTI_ARRAY_TARGET), types.toString());
        assertTrue(types.contains(ReferenceFixture.CONSTANT_TARGET), types.toString());
    }

    @Test
    void leavesDeclaredInterfacesOutOfTheBytecodeReferences() {
        List<String> types = indexed().references().types().stream().map(TypeReference::internalName).toList();

        assertFalse(types.contains("java/lang/Runnable"), types.toString());
    }

    @Test
    void namesTheCallSiteForEveryReference() {
        ClassReferences references = indexed().references();

        assertTrue(references.types().stream()
                .allMatch(type -> type.referencingMethod().equals(ReferenceFixture.METHOD)));
        assertTrue(references.members().stream()
                .allMatch(member -> member.referencingMethod().equals(ReferenceFixture.METHOD)));
    }

    @Test
    void capturesFieldAndInvocationKindsSeparately() {
        List<MemberReference> members = indexed().references().members();

        assertTrue(members.contains(new MemberReference(ReferenceKind.GET_STATIC, ReferenceFixture.TARGET,
                "FLAG", "Z", ReferenceFixture.METHOD, Optional.empty())), members.toString());
        assertTrue(members.contains(new MemberReference(ReferenceKind.PUT_STATIC, ReferenceFixture.TARGET,
                "FLAG", "Z", ReferenceFixture.METHOD, Optional.empty())), members.toString());
        assertTrue(members.contains(new MemberReference(ReferenceKind.INVOKE_SPECIAL, ReferenceFixture.TARGET,
                "<init>", "()V", ReferenceFixture.METHOD, Optional.empty())), members.toString());
        assertTrue(members.contains(new MemberReference(ReferenceKind.INVOKE_STATIC, ReferenceFixture.TARGET,
                "reset", "()V", ReferenceFixture.METHOD, Optional.empty())), members.toString());
    }

    @Test
    void capturesTheSourceFileAndTheLineOfEveryReference() {
        IndexedClass declared = traced(SourceDebugFixture.tracedCaller(
                LinkageFixture.CALLER,
                List.of(
                        LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ReferenceFixture.TARGET, "first", "()V"),
                        LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ReferenceFixture.TARGET, "second", "()V"))));

        assertEquals(Optional.of(SourceDebugFixture.SOURCE_FILE), declared.sourceFile());
        assertEquals(
                List.of(Optional.of(SourceDebugFixture.lineOf(0)), Optional.of(SourceDebugFixture.lineOf(1))),
                declared.references().members().stream().map(MemberReference::line).toList());
    }

    @Test
    void capturesTheLineOfATypeInstruction() {
        IndexedClass declared =
                traced(SourceDebugFixture.tracedTypeCaller(LinkageFixture.CALLER, List.of(ReferenceFixture.TARGET)));

        assertEquals(
                List.of(Optional.of(SourceDebugFixture.lineOf(0))),
                declared.references().types().stream().map(TypeReference::line).toList());
    }

    @Test
    void recordsNoLineForAClassCompiledWithoutALineTable() {
        IndexedClass declared = traced(SourceDebugFixture.untracedCaller(
                LinkageFixture.CALLER,
                List.of(LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, ReferenceFixture.TARGET, "first", "()V"))));

        assertEquals(Optional.of(SourceDebugFixture.SOURCE_FILE), declared.sourceFile());
        assertEquals(
                List.of(Optional.empty()),
                declared.references().members().stream().map(MemberReference::line).toList());
    }

    @Test
    void recordsNoSourceForAClassCompiledWithoutDebugInformation() {
        IndexedClass declared = indexed();

        assertTrue(declared.sourceFile().isEmpty());
        assertTrue(declared.references().types().stream().allMatch(type -> type.line().isEmpty()),
                declared.references().types().toString());
        assertTrue(declared.references().members().stream().allMatch(member -> member.line().isEmpty()),
                declared.references().members().toString());
    }

    @Test
    void followsHandlesBootstrapMethodsAndDynamicConstants() {
        List<String> owners = indexed().references().members().stream()
                .filter(member -> member.kind() == ReferenceKind.METHOD_HANDLE)
                .map(MemberReference::ownerInternalName)
                .toList();

        assertTrue(owners.contains(ReferenceFixture.HANDLE_OWNER), owners.toString());
        assertTrue(owners.contains(ReferenceFixture.BOOTSTRAP_OWNER), owners.toString());
        assertTrue(owners.contains(ReferenceFixture.CONSTANT_OWNER), owners.toString());
        assertTrue(owners.contains(ReferenceFixture.NESTED_CONSTANT_OWNER), owners.toString());
    }

    /**
     * The nesting ceiling is read from both sides. A constant chain is followed to the deepest level the
     * ceiling allows and stops there, so a hostile constant pool cannot walk the reader down forever and
     * an ordinary nest is still read whole.
     */
    @Test
    void followsADynamicConstantChainAsDeepAsTheCeilingAndNoDeeper() {
        List<String> owners = indexed().references().members().stream()
                .filter(member -> member.kind() == ReferenceKind.METHOD_HANDLE)
                .map(MemberReference::ownerInternalName)
                .toList();

        assertTrue(owners.contains(ReferenceFixture.DEEPEST_CONSTANT_OWNER), owners.toString());
        assertFalse(owners.contains(ReferenceFixture.BEYOND_DEPTH_OWNER), owners.toString());
    }

    @Test
    void reportsNoReferencesForAClassThatReachesForNothing() {
        Path archive = EngineFixture.jar(workspace, "lib/plain.jar",
                EngineFixture.entries("com/acme/plain/Plain.class", EngineFixture.classFile("com/acme/plain/Plain")));

        IndexedClass declared = classes(archive).get(0);

        assertEquals(ClassReferences.empty(), declared.references());
        assertEquals(64, declared.digest().length());
    }

    private IndexedClass indexed() {
        Path archive = EngineFixture.jar(workspace, "lib/references.jar",
                EngineFixture.entries(ReferenceFixture.OWNER + ".class", ReferenceFixture.classFile()));
        return classes(archive).get(0);
    }

    private IndexedClass traced(byte[] classFile) {
        Path archive = EngineFixture.jar(workspace, "lib/traced.jar",
                EngineFixture.entries(LinkageFixture.CALLER + ".class", classFile));
        return classes(archive).get(0);
    }

    private List<IndexedClass> classes(Path archive) {
        return ArtifactCatalog
                .read(EngineFixture.request(List.of(archive), List.of(), 17), new ResourceBudget())
                .artifacts()
                .get(0)
                .classes();
    }
}
