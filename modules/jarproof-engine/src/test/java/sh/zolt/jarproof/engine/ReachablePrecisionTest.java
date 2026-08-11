package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/**
 * Where rapid type analysis draws its two lines: the edges it must follow, and the ones it must refuse.
 *
 * <p>{@link ReachabilityTest} proves the graph reconstructs a chain it did reach. What this measures is
 * the opposite pair of failures, both of which a suite that only counts findings cannot see. An edge
 * dropped makes the mode quietly miss a real break, and an edge invented makes it as loud as
 * {@link Scope#ALL} while still calling itself proof. Every case here therefore reads the reachable
 * scope against a second, louder scope over the same corpus: the break is really in the bytes, and
 * whether it is reported is exactly the question of which types this run can allocate.
 */
final class ReachablePrecisionTest {
    private static final String MAIN_CLASS = "com/acme/app/Entry";
    private static final String SUB = "com/acme/app/Sub";
    private static final String ALPHA = "com/acme/lib/Alpha";
    private static final String BASE = "com/acme/lib/Base";
    private static final String HELPER = "com/acme/lib/Helper";
    private static final String WIDGET = "com/acme/lib/Widget";
    private static final String TASK = "com/acme/lib/Task";
    private static final String ABSENT = "com/acme/gone/Absent";
    private static final String PLATFORM_NAME = "java/lang/String";
    private static final String MISSING_CLASS = "JP1001";

    @TempDir
    Path workspace;

    /**
     * The first-party surface is allocated as well as entered. A library override reachable only through
     * a receiver the application declares is the ordinary shape of a framework integration, and a run
     * that allocated nothing it was not shown a {@code new} for would call that override dead.
     */
    @Test
    void provesALibraryOverrideOnlyAFirstPartyReceiverCanUnlock() {
        Map<String, byte[]> entries = LinkageFixture.classes(MAIN_CLASS, ReachableFixture.type(
                MAIN_CLASS, ReachableFixture.body(
                        ReachableFixture.MAIN, ReachableFixture.call(ALPHA, ReachableFixture.WORK))));
        LinkageFixture.and(entries, SUB, ReachableFixture.type(
                SUB, BASE, List.of(), ReachableFixture.body(ReachableFixture.CONSTRUCTOR)));
        Path application = EngineFixture.jar(workspace, "first-party.jar", entries);
        Path library = brokenBase();

        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.REACHABLE));
    }

    /**
     * A dispatched call edges to the declaration resolution finds, whether or not this run can allocate
     * a receiver. The declaration is what a launcher would link the call site against, so dropping that
     * edge would hide every break in a library method reached through an interface nobody instantiates
     * on the classpath being analysed.
     */
    @Test
    void edgesToTheDeclarationADispatchedCallResolvesWithNoReceiverAllocated() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.dispatch(ALPHA, ReachableFixture.HANDLE))));
        Path library = jar("declared", LinkageFixture.classes(ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.body(
                        ReachableFixture.HANDLE, ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));

        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.REACHABLE));
    }

    /**
     * Naming an instance method initializes nothing. The JVM runs a class initializer when the class is
     * allocated or when a reference names one of its static members, so a virtual call that reaches an
     * instance method must leave that class's static setup out of the graph.
     */
    @Test
    void initializesNoClassThatOnlyAVirtualCallNames() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.dispatch(ALPHA, ReachableFixture.HANDLE))));
        Path library = jar("initializer", LinkageFixture.classes(ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.HANDLE),
                        ReachableFixture.INITIALIZER,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));

        assertEquals(List.of(), codes(application, library, Scope.REACHABLE));
        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.ALL));
    }

    /**
     * A static call runs the declaration and nothing else. Expanding it to the overrides an allocated
     * subclass declares would report a method no receiver can route to, which is the class-hierarchy
     * imprecision this mode exists to avoid.
     */
    @Test
    void dispatchesNoDirectCallIntoAnAllocatedSubclass() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.call(BASE, ReachableFixture.WORK),
                ReachableFixture.construct(SUB))));
        Path library = overriddenBase();

        assertEquals(List.of(), codes(application, library, Scope.REACHABLE));
        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.ALL));
    }

    /**
     * A constructor that allocates a different class allocated something. Only {@code super(...)} and
     * {@code this(...)} continue an allocation somebody else made, so treating every constructor call
     * written inside a constructor as chaining would lose the receiver a factory brings into existence.
     */
    @Test
    void treatsAConstructorAllocatingAnotherClassAsARealAllocation() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.construct(WIDGET),
                ReachableFixture.call(ALPHA, ReachableFixture.WORK))));
        Path library = factory();

        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.REACHABLE));
    }

    /**
     * Naming a type allocates nothing. A virtual call on a class this run never constructs proves a
     * receiver of that shape can exist nowhere, so the overrides it declares stay out of the graph even
     * though the call site mentions the class by name.
     */
    @Test
    void allocatesNothingForAReferenceThatMerelyNamesAType() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.call(ALPHA, ReachableFixture.WORK),
                ReachableFixture.dispatch(SUB, ReachableFixture.HASH_CODE))));
        Path library = unallocatedImplementation();

        assertEquals(List.of(), codes(application, library, Scope.REACHABLE));
        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.ALL));
    }

    /**
     * Allocating a class runs its static setup. A {@code new} is the other half of the initialization
     * rule the JVM applies, so a break written in the initializer of a class this run can allocate is
     * reachable even though no reference names a static member of it.
     */
    @Test
    void initializesEveryClassThisRunCanAllocate() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.construct(WIDGET))));
        Path library = jar("allocated", LinkageFixture.classes(WIDGET, ReachableFixture.type(
                WIDGET, ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.INITIALIZER,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));

        assertEquals(List.of(MISSING_CLASS), codes(application, library, Scope.REACHABLE));
    }

    /**
     * A shipped copy of a class the target runtime supplies joins no call graph. The runtime runs its own
     * copy, so counting the shadowed one would inflate the closure the run reports and would presume a
     * first-party method live that no launcher will ever load.
     */
    @Test
    void countsNoMethodOfAClassTheTargetRuntimeAlreadySupplies() {
        Path bare = EngineFixture.jar(workspace, "bare.jar", LinkageFixture.classes(MAIN_CLASS, entryClass()));
        Map<String, byte[]> shadowing = LinkageFixture.classes(MAIN_CLASS, entryClass());
        LinkageFixture.and(shadowing, PLATFORM_NAME, ReachableFixture.type(
                PLATFORM_NAME, ReachableFixture.body(
                        ReachableFixture.WORK, ReachableFixture.call(ABSENT, ReachableFixture.WORK))));
        Path shadowed = EngineFixture.jar(workspace, "shadowing.jar", shadowing);

        ReachableMethods graph = ReachableFixture.graph(List.of(shadowed), List.of());

        assertEquals(ReachableFixture.graph(List.of(bare), List.of()).declared(), graph.declared());
        assertFalse(graph.reaches(PLATFORM_NAME, ReachableFixture.WORK));
    }

    /**
     * An interface call reaches the declaration the interface itself provides. That node is what a
     * receiver allocated later is matched against, so losing it would leave the call site answering to
     * nothing at all.
     */
    @Test
    void reachesTheInterfaceDeclarationAnInterfaceCallResolves() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN, ReachableFixture.dispatchThrough(TASK, ReachableFixture.HANDLE))));
        Path library = jar("contract", LinkageFixture.classes(TASK, task()));

        ReachableMethods graph = ReachableFixture.graph(List.of(application), List.of(library));

        assertTrue(graph.reaches(TASK, ReachableFixture.HANDLE));
    }

    /** A library base whose override breaks, reachable only through a first-party subclass. */
    private Path brokenBase() {
        Map<String, byte[]> entries = LinkageFixture.classes(TASK, task());
        LinkageFixture.and(entries, BASE, ReachableFixture.type(
                BASE, EngineFixture.OBJECT, List.of(TASK), ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.HANDLE,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK))));
        return jar("base", dispatcher(entries));
    }

    /** A base whose static method is clean and a subclass whose override of it is not. */
    private Path overriddenBase() {
        Map<String, byte[]> entries = LinkageFixture.classes(BASE, ReachableFixture.type(
                BASE, ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR), ReachableFixture.WORK)));
        LinkageFixture.and(entries, SUB, ReachableFixture.type(
                SUB, BASE, List.of(), ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.WORK,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK))));
        return jar("overridden", entries);
    }

    /** A widget whose constructor allocates the one implementation of a dispatched interface. */
    private Path factory() {
        Map<String, byte[]> entries = LinkageFixture.classes(TASK, task());
        LinkageFixture.and(entries, HELPER, ReachableFixture.type(
                HELPER, EngineFixture.OBJECT, List.of(TASK), ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.HANDLE,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK))));
        LinkageFixture.and(entries, WIDGET, ReachableFixture.type(
                WIDGET, ReachableFixture.body(
                        ReachableFixture.CONSTRUCTOR, ReachableFixture.construct(HELPER))));
        return jar("factory", dispatcher(entries));
    }

    /** An implementation nothing allocates, whose innocuous method the application does call. */
    private Path unallocatedImplementation() {
        Map<String, byte[]> entries = LinkageFixture.classes(TASK, task());
        LinkageFixture.and(entries, SUB, ReachableFixture.type(
                SUB, EngineFixture.OBJECT, List.of(TASK), ReachableFixture.and(
                        ReachableFixture.and(
                                ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                                ReachableFixture.HANDLE,
                                ReachableFixture.call(ABSENT, ReachableFixture.WORK)),
                        ReachableFixture.HASH_CODE)));
        return jar("unallocated", dispatcher(entries));
    }

    /** Adds the library call site that dispatches through the interface without naming an implementation. */
    private static Map<String, byte[]> dispatcher(Map<String, byte[]> entries) {
        return LinkageFixture.and(entries, ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.body(
                        ReachableFixture.WORK,
                        ReachableFixture.dispatchThrough(TASK, ReachableFixture.HANDLE))));
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

    private static byte[] entryClass() {
        return ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(ReachableFixture.MAIN));
    }

    private Path app(byte[] classFile) {
        return EngineFixture.jar(workspace, "app.jar", LinkageFixture.classes(MAIN_CLASS, classFile));
    }

    private Path jar(String name, Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "lib/" + name + ".jar", entries);
    }

    private static List<String> codes(Path application, Path library, Scope scope) {
        List<Finding> findings = LinkageFixture.check(List.of(application), List.of(library), scope);
        return EngineFixture.codes(findings);
    }
}
