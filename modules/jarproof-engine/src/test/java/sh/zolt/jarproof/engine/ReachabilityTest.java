package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/** The call graph itself: what it counts, how it explains a reached method, and where it stops. */
final class ReachabilityTest {
    private static final String MAIN_CLASS = "com/acme/app/Entry";
    private static final String ALPHA = "com/acme/lib/Alpha";
    private static final String BETA = "com/acme/lib/Beta";
    private static final String TASK = "com/acme/lib/Task";
    private static final String BASE = "com/acme/lib/Base";
    private static final String SUB = "com/acme/lib/Sub";
    private static final String REGISTRY = "com/acme/lib/Registry";
    private static final String ABSENT = "com/acme/gone/Absent";
    private static final String SECOND_ABSENT = "com/acme/gone/Other";
    private static final String PLATFORM_NAME = "java/lang/String";
    private static final String LINKAGE_ERROR = "java/lang/LinkageError";
    private static final String ORDINARY_ERROR = "java/lang/IllegalStateException";
    private static final String MISSING_CLASS = "JP1001";

    @TempDir
    Path workspace;

    /**
     * The chain is the debugging story: a reader has to be able to see which first-party method starts
     * the path to a library method they believed was dead.
     */
    @Test
    void reconstructsTheChainThatReachedALibraryMethod() {
        ReachableMethods graph = ReachableFixture.graph(List.of(app(caller())), List.of(chain()));

        assertEquals(
                MAIN_CLASS + "#" + ReachableFixture.MAIN
                        + " -> " + ALPHA + "#" + ReachableFixture.WORK
                        + " -> " + BETA + "#" + ReachableFixture.WORK,
                graph.chain(BETA, ReachableFixture.WORK));
    }

    /** A method the graph never reached explains itself as exactly that: one step, going nowhere. */
    @Test
    void answersWithTheMethodAloneWhenNothingReachedIt() {
        ReachableMethods graph = ReachableFixture.graph(List.of(app(caller())), List.of(chain()));

        assertFalse(graph.reaches(ALPHA, ReachableFixture.UNUSED));
        assertEquals(ALPHA + "#" + ReachableFixture.UNUSED, graph.chain(ALPHA, ReachableFixture.UNUSED));
    }

    /**
     * Both closure sizes are reported, because a run that proved almost everything reachable has proved
     * nothing worth trusting. The application surface plus the two library methods it reaches is a
     * strict subset of what the classpath declares.
     */
    @Test
    void countsTheMethodsItProvedAgainstTheMethodsThereWere() {
        ReachableMethods graph = ReachableFixture.graph(List.of(app(caller())), List.of(chain()));

        assertTrue(graph.reached().size() < graph.declared(),
                graph.reached().size() + " of " + graph.declared());
        assertTrue(graph.reaches(MAIN_CLASS, ReachableFixture.MAIN));
        assertTrue(graph.reaches(ALPHA, ReachableFixture.WORK));
    }

    /**
     * A handler for a linkage failure is the author saying the reference below may be absent, so nothing
     * below it is reachable. A handler for anything else says no such thing.
     */
    @Test
    void followsNoEdgeOutOfAMethodThatDeclaresItAbsorbsALinkageFailure() {
        Path absorbing = probe("absorbing", LINKAGE_ERROR);
        Path ordinary = probe("ordinary", ORDINARY_ERROR);
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.call(ALPHA, ReachableFixture.WORK))));

        assertEquals(List.of(), EngineFixture.codes(check(application, absorbing, Scope.REACHABLE)));
        assertEquals(
                List.of(MISSING_CLASS), EngineFixture.codes(check(application, ordinary, Scope.REACHABLE)));
        assertEquals(List.of(MISSING_CLASS), EngineFixture.codes(check(application, absorbing, Scope.ALL)));
    }

    /**
     * A constructor calling {@code super} continues an allocation somebody else made. Counting it would
     * put the base class in the allocated set and pull its own broken implementation into the graph
     * through an interface call that only ever sees the subclass.
     */
    @Test
    void treatsASuperConstructorCallAsChainingRatherThanAnAllocation() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.construct(SUB),
                ReachableFixture.dispatchThrough(TASK, ReachableFixture.HANDLE))));
        Path library = hierarchy();

        assertEquals(List.of(), EngineFixture.codes(check(application, library, Scope.REACHABLE)));
        assertEquals(List.of(MISSING_CLASS), EngineFixture.codes(check(application, library, Scope.ALL)));
    }

    /**
     * Four shapes that name no reachable bytecode at all: a member of an array type, an allocation of a
     * platform class, a call into a class the target runtime supplies even though the classpath declares
     * it too, and a method handle naming a field rather than a method. None may reach anything, and none
     * may fail either.
     */
    @Test
    void movesNothingForAReferenceThatNamesNoClasspathMethod() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.dispatch("[Ljava/lang/Object;", "clone()Ljava/lang/Object;"),
                ReachableFixture.construct(EngineFixture.OBJECT),
                ReachableFixture.call(PLATFORM_NAME, "valueOf(I)Ljava/lang/String;"),
                ReachableFixture.dispatch(ALPHA, LinkageFixture.RUN))));
        Map<String, byte[]> entries = LinkageFixture.classes(
                ALPHA, LinkageFixture.caller(ALPHA, List.of(LinkageFixture.reference(
                        ReferenceKind.METHOD_HANDLE, BETA, ReachableFixture.FLAG, ReachableFixture.INT_FIELD))));
        LinkageFixture.and(entries, BETA, ReachableFixture.type(BETA, ReachableFixture.body(
                ReachableFixture.WORK)));
        LinkageFixture.and(entries, PLATFORM_NAME, EngineFixture.classFile(PLATFORM_NAME));
        Path library = EngineFixture.jar(workspace, "lib/handles.jar", entries);

        assertEquals(List.of(), EngineFixture.codes(check(application, library, Scope.REACHABLE)));
        assertFalse(ReachableFixture.graph(List.of(application), List.of(library))
                .reaches(PLATFORM_NAME, "valueOf(I)Ljava/lang/String;"));
    }

    /**
     * The fixpoint has to grow in both directions. Here the virtual call site is expanded before
     * anything has been allocated, and the receiver arrives afterwards from a class initializer — the
     * singleton-in-a-static-field shape, which is why a class initializer's allocations count.
     */
    @Test
    void answersACallSiteAlreadyRecordedWhenAnInitializerAllocatesTheReceiverLater() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.call(ALPHA, ReachableFixture.WORK),
                ReachableFixture.read(REGISTRY))));
        Path library = singleton();

        List<Finding> reachable = check(application, library, Scope.REACHABLE);

        assertEquals(List.of(MISSING_CLASS), EngineFixture.codes(reachable));
        assertEquals(ABSENT, reachable.get(0).subject());
        assertEquals(
                MAIN_CLASS + "#" + ReachableFixture.MAIN
                        + " -> " + REGISTRY + "#" + ReachableFixture.INITIALIZER
                        + " -> " + SUB + "#" + ReachableFixture.CONSTRUCTOR,
                ReachableFixture.graph(List.of(application), List.of(library))
                        .chain(SUB, ReachableFixture.CONSTRUCTOR));
    }

    /**
     * Every method a class declares is expanded, not one method per class. The worklist is ordered by
     * class and then by signature, so an ordering that stopped at the class name would make two methods
     * of one class the same worklist entry: one of them would be analysed, and the break written in the
     * other would be reported as unreachable.
     */
    @Test
    void expandsEveryMethodOfOneClassRatherThanOnePerClass() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.and(
                ReachableFixture.body(ReachableFixture.MAIN, ReachableFixture.call(ABSENT, ReachableFixture.WORK)),
                ReachableFixture.WORK,
                ReachableFixture.call(SECOND_ABSENT, ReachableFixture.WORK))));

        List<Finding> findings = LinkageFixture.check(List.of(application), List.of(), Scope.REACHABLE);

        assertEquals(List.of(MISSING_CLASS, MISSING_CLASS), EngineFixture.codes(findings));
        assertEquals(List.of(ABSENT, SECOND_ABSENT), findings.stream().map(Finding::subject).sorted().toList());
    }

    /** A class whose bytes no parser accepted keeps its own diagnostic and joins no call graph. */
    @Test
    void readsPastAnEntryNoParserAccepted() {
        Map<String, byte[]> entries = LinkageFixture.classes(MAIN_CLASS, caller());
        entries.put("com/acme/app/Broken.class", "not bytecode".getBytes(StandardCharsets.UTF_8));
        Path application = EngineFixture.jar(workspace, "corrupt.jar", entries);

        ReachableMethods graph = ReachableFixture.graph(List.of(application), List.of(chain()));

        assertTrue(graph.reaches(MAIN_CLASS, ReachableFixture.MAIN));
        assertTrue(
                EngineFixture.coded(
                                EngineFixture.verify(List.of(application), List.of(chain()), 17), "JP3004")
                        .isPresent(),
                "the unreadable entry keeps its own diagnostic");
    }

    private byte[] caller() {
        return ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.call(ALPHA, ReachableFixture.WORK)));
    }

    private Path chain() {
        Map<String, byte[]> entries = LinkageFixture.classes(ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.and(
                        ReachableFixture.body(
                                ReachableFixture.WORK, ReachableFixture.call(BETA, ReachableFixture.WORK)),
                        ReachableFixture.UNUSED,
                        ReachableFixture.call(BETA, ReachableFixture.UNUSED))));
        return EngineFixture.jar(workspace, "lib/chain.jar", LinkageFixture.and(
                entries, BETA, ReachableFixture.type(BETA, ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.WORK),
                        ReachableFixture.UNUSED))));
    }

    private Path probe(String name, String caught) {
        Map<String, byte[]> entries = LinkageFixture.classes(
                ALPHA, ReachableFixture.probe(ALPHA, caught, List.of(
                        ReachableFixture.call(BETA, ReachableFixture.WORK))));
        return EngineFixture.jar(workspace, "lib/" + name + ".jar", LinkageFixture.and(
                entries, BETA, ReachableFixture.type(BETA, ReachableFixture.body(
                        ReachableFixture.WORK, ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));
    }

    /**
     * A registry whose initializer allocates the one implementation, and a library call site that
     * dispatches through the interface without knowing which implementation exists. The subclass
     * constructor delegates to its own class as well, which allocates nothing new either.
     */
    private Path singleton() {
        Map<String, byte[]> entries = LinkageFixture.classes(TASK, task());
        LinkageFixture.and(entries, SUB, ReachableFixture.type(
                SUB, EngineFixture.OBJECT, List.of(TASK), ReachableFixture.and(
                        ReachableFixture.body(
                                ReachableFixture.CONSTRUCTOR, ReachableFixture.construct(SUB)),
                        ReachableFixture.HANDLE,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK))));
        LinkageFixture.and(entries, ALPHA, ReachableFixture.type(ALPHA, ReachableFixture.body(
                ReachableFixture.WORK, ReachableFixture.dispatchThrough(TASK, ReachableFixture.HANDLE))));
        LinkageFixture.and(entries, REGISTRY, ReachableFixture.type(REGISTRY, ReachableFixture.body(
                ReachableFixture.INITIALIZER, ReachableFixture.construct(SUB))));
        return EngineFixture.jar(workspace, "lib/singleton.jar", entries);
    }

    private Path hierarchy() {
        Map<String, byte[]> entries = LinkageFixture.classes(TASK, task());
        LinkageFixture.and(entries, BASE, ReachableFixture.type(
                BASE, EngineFixture.OBJECT, List.of(TASK), ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.HANDLE,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK))));
        LinkageFixture.and(entries, SUB, ReachableFixture.type(
                SUB, BASE, List.of(), ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR), ReachableFixture.HANDLE)));
        return EngineFixture.jar(workspace, "lib/hierarchy.jar", entries);
    }

    private static byte[] task() {
        return LinkageFixture.type(
                TASK,
                LinkageFixture.PUBLIC_INTERFACE,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member(
                        "handle", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)));
    }

    private Path app(byte[] classFile) {
        return EngineFixture.jar(workspace, "app.jar", LinkageFixture.classes(MAIN_CLASS, classFile));
    }

    private static List<Finding> check(Path application, Path library, Scope scope) {
        return LinkageFixture.check(List.of(application), List.of(library), scope);
    }
}
