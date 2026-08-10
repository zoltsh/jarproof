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

/**
 * What {@link Scope#REACHABLE} adds and what it removes, one edge rule at a time.
 *
 * <p>Every case is the same shape: a break somewhere in a library, and an application that either does
 * or does not have a static path to it. The three scopes are read off the same classpath wherever the
 * contrast is the point, because the claim is not that reachability finds something — it is that
 * reachability tells two library breaks apart that the other two scopes cannot.
 */
final class LinkageReachabilityTest {
    private static final String MAIN_CLASS = "com/acme/app/Entry";
    private static final String ALPHA = "com/acme/lib/Alpha";
    private static final String BETA = "com/acme/lib/Beta";
    private static final String HANDLER = "com/acme/lib/Handler";
    private static final String LIVE = "com/acme/lib/Live";
    private static final String DEAD = "com/acme/lib/Dead";
    private static final String HOLDER = "com/acme/lib/Holder";
    private static final String BASE = "com/acme/lib/Base";
    private static final String LAMBDAS = "com/acme/lib/Lambdas";
    private static final String ABSENT = "com/acme/gone/Absent";
    private static final String SECOND_ABSENT = "com/acme/gone/Other";
    private static final String MISSING_CLASS = "JP1001";
    private static final String MISSING_METHOD = "JP1003";

    @TempDir
    Path workspace;

    /**
     * The contrast the mode exists for. One library method calls another library's absent method, and
     * the application has a static path to the first: an error when reachability is proved, a warning
     * when every origin is merely inspected, and absent when only application bytecode is read.
     *
     * <p>The promotion has to be arguable, so the promoted finding also shows the path that promoted it:
     * the first-party method that starts it, and the library method the break is written in. Neither
     * quieter mode may show one, because neither proved one — that is the regression this asserts, and it
     * is what keeps a run at either quieter scope byte for byte what it was before this mode existed.
     */
    @Test
    void promotesALibraryBreakTheApplicationReallyReachesAndOmitsItOtherwise() {
        Path application = app(ReachableFixture.type(
                MAIN_CLASS, ReachableFixture.body(
                        ReachableFixture.MAIN, ReachableFixture.call(ALPHA, ReachableFixture.WORK))));
        Path library = chain();

        List<Finding> reachable = check(application, library, Scope.REACHABLE);
        List<Finding> all = check(application, library, Scope.ALL);

        assertEquals(List.of(MISSING_METHOD), EngineFixture.codes(reachable));
        assertEquals(Severity.ERROR, reachable.get(0).severity());
        assertEquals(List.of(MISSING_METHOD, MISSING_METHOD), EngineFixture.codes(all));
        assertEquals(Severity.WARNING, all.get(0).severity());
        assertEquals(List.of(), EngineFixture.codes(check(application, library, Scope.APPLICATION)));
        assertEquals(
                List.of("reachable via: " + MAIN_CLASS + "#" + ReachableFixture.MAIN
                        + " -> " + ALPHA + "#" + ReachableFixture.WORK),
                chains(reachable),
                EngineFixture.evidence(reachable.get(0)).toString());
        assertEquals(List.of(), chains(all), "the wider scope proves no path, so it quotes none");
    }

    /**
     * The break in the library method nothing calls is the one that has to disappear. Both breaks are
     * in the same class, so only the method-level graph can separate them.
     */
    @Test
    void omitsABreakInALibraryMethodNothingCalls() {
        Path application = app(ReachableFixture.type(
                MAIN_CLASS, ReachableFixture.body(
                        ReachableFixture.MAIN, ReachableFixture.call(ALPHA, ReachableFixture.WORK))));
        Path library = chain();

        List<Finding> reachable = check(application, library, Scope.REACHABLE);
        List<Finding> all = check(application, library, Scope.ALL);

        assertEquals(1, reachable.size(), reachable.toString());
        assertTrue(subjects(reachable).contains(BETA + "#missing()V"), subjects(reachable).toString());
        assertTrue(subjects(all).contains(BETA + "#unreached()V"), subjects(all).toString());
    }

    /**
     * Rapid type analysis, not class hierarchy analysis. Two subclasses override the same method and
     * both are broken; the application allocates one of them. Reporting the override of the type nobody
     * can construct is exactly the noise this mode is supposed to remove.
     *
     * <p>The allocation is written twice, because allocating one type twice is still one type and one
     * edge, and a graph that counted it twice would be growing when it should be settling.
     */
    @Test
    void dispatchesOnlyIntoTheOverrideOfATypeReachableCodeAllocates() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.construct(LIVE),
                ReachableFixture.construct(LIVE),
                ReachableFixture.dispatch(HANDLER, ReachableFixture.HANDLE))));

        List<Finding> reachable = check(application, hierarchy(), Scope.REACHABLE);

        assertEquals(List.of(MISSING_CLASS), EngineFixture.codes(reachable));
        assertEquals(ABSENT, reachable.get(0).subject());
        assertTrue(subjects(check(application, hierarchy(), Scope.ALL)).contains(SECOND_ABSENT),
                "the override nobody allocates is still library evidence under the all scope");
    }

    /**
     * A static field read initializes the declaring class, and initialization runs upwards, so a break
     * in either initializer is reachable through a reference that names neither method.
     */
    @Test
    void reachesEveryInitializerAStaticFieldReadRuns() {
        Path application = app(ReachableFixture.type(
                MAIN_CLASS, ReachableFixture.body(ReachableFixture.MAIN, ReachableFixture.read(HOLDER))));

        List<Finding> reachable = check(application, initializers(), Scope.REACHABLE);

        assertEquals(List.of(MISSING_CLASS, MISSING_CLASS), EngineFixture.codes(reachable));
        assertEquals(List.of(ABSENT, SECOND_ABSENT), subjects(reachable).stream().sorted().toList());
    }

    /**
     * A lambda body is named by a bootstrap argument and by nothing else, so the only way to reach it is
     * through the method handle. Reaching it once the capture site is reachable over-approximates on
     * purpose, and that is the behaviour under test.
     */
    @Test
    void reachesALambdaBodyThroughItsMethodHandle() {
        Path application = app(ReachableFixture.type(
                MAIN_CLASS, ReachableFixture.body(
                        ReachableFixture.MAIN, ReachableFixture.call(LAMBDAS, ReachableFixture.CAPTURE))));

        List<Finding> reachable = check(application, lambdas(), Scope.REACHABLE);

        assertEquals(List.of(MISSING_CLASS), EngineFixture.codes(reachable));
        assertEquals(ABSENT, reachable.get(0).subject());
    }

    /**
     * The named limitation, asserted rather than assumed. The application allocates a key and hands it
     * to a platform collection; the platform's own call back into {@code hashCode} is invisible because
     * the traversal stops at the runtime, so a break inside that override is omitted here and remains a
     * warning under the wider scope.
     */
    @Test
    void doesNotTraverseAPlatformCallbackBackIntoLibraryCode() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.construct(HOLDER),
                ReachableFixture.dispatch("java/util/HashSet", "add(Ljava/lang/Object;)Z"))));
        Path library = EngineFixture.jar(workspace, "lib/keys.jar", LinkageFixture.classes(
                HOLDER, ReachableFixture.type(HOLDER, ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.HASH_CODE,
                        ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));

        assertEquals(List.of(), EngineFixture.codes(check(application, library, Scope.REACHABLE)));
        assertEquals(List.of(MISSING_CLASS), EngineFixture.codes(check(application, library, Scope.ALL)));
    }

    /** The graph is a set, and the worklist is drained in one fixed order, so entry order cannot show. */
    @Test
    void reportsTheSameFindingsWhateverOrderTheEntriesWereWrittenIn() {
        Path application = app(ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                ReachableFixture.MAIN,
                ReachableFixture.construct(LIVE),
                ReachableFixture.dispatch(HANDLER, ReachableFixture.HANDLE),
                ReachableFixture.call(ALPHA, ReachableFixture.WORK))));

        List<Finding> ordered = check(application, mixed(false), Scope.REACHABLE);
        List<Finding> shuffled = check(application, mixed(true), Scope.REACHABLE);

        assertEquals(subjects(ordered), subjects(shuffled));
        assertTrue(ordered.size() > 1, ordered.toString());
    }

    private Path chain() {
        Map<String, byte[]> entries = LinkageFixture.classes(ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.and(
                        ReachableFixture.body(
                                ReachableFixture.WORK, ReachableFixture.call(BETA, "missing()V")),
                        ReachableFixture.UNUSED,
                        ReachableFixture.call(BETA, "unreached()V"))));
        return EngineFixture.jar(workspace, "lib/chain.jar", LinkageFixture.and(
                entries, BETA, ReachableFixture.type(BETA, ReachableFixture.body(ReachableFixture.WORK))));
    }

    private Path hierarchy() {
        return EngineFixture.jar(workspace, "lib/hierarchy.jar", overrides(false));
    }

    private Path mixed(boolean shuffle) {
        Map<String, byte[]> entries = overrides(shuffle);
        entries.putAll(LinkageFixture.classes(ALPHA, ReachableFixture.type(
                ALPHA, ReachableFixture.body(
                        ReachableFixture.WORK, ReachableFixture.call(BETA, "missing()V")))));
        return EngineFixture.jar(workspace, "lib/mixed-" + shuffle + ".jar", entries);
    }

    private Map<String, byte[]> overrides(boolean reversed) {
        Map<String, byte[]> entries = LinkageFixture.classes(HANDLER, ReachableFixture.type(
                HANDLER, ReachableFixture.and(
                        ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                        ReachableFixture.HANDLE)));
        byte[] live = subclass(LIVE, ABSENT);
        byte[] dead = subclass(DEAD, SECOND_ABSENT);
        LinkageFixture.and(entries, reversed ? DEAD : LIVE, reversed ? dead : live);
        LinkageFixture.and(entries, reversed ? LIVE : DEAD, reversed ? live : dead);
        return entries;
    }

    private byte[] subclass(String internalName, String broken) {
        return ReachableFixture.type(internalName, HANDLER, List.of(), ReachableFixture.and(
                ReachableFixture.body(ReachableFixture.CONSTRUCTOR),
                ReachableFixture.HANDLE,
                ReachableFixture.call(broken, ReachableFixture.WORK)));
    }

    private Path initializers() {
        Map<String, byte[]> entries = LinkageFixture.classes(BASE, ReachableFixture.type(
                BASE, ReachableFixture.body(
                        ReachableFixture.INITIALIZER, ReachableFixture.call(SECOND_ABSENT, ReachableFixture.WORK))));
        return EngineFixture.jar(workspace, "lib/initializers.jar", LinkageFixture.and(
                entries, HOLDER, ReachableFixture.type(HOLDER, BASE, List.of(), ReachableFixture.body(
                        ReachableFixture.INITIALIZER, ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));
    }

    private Path lambdas() {
        return EngineFixture.jar(workspace, "lib/lambdas.jar", LinkageFixture.classes(
                LAMBDAS, ReachableFixture.lambdaHolder(
                        LAMBDAS, List.of(ReachableFixture.call(ABSENT, ReachableFixture.WORK)))));
    }

    private Path app(byte[] classFile) {
        return EngineFixture.jar(workspace, "app.jar", LinkageFixture.classes(MAIN_CLASS, classFile));
    }

    private static List<Finding> check(Path application, Path library, Scope scope) {
        return LinkageFixture.check(List.of(application), List.of(library), scope);
    }

    private static List<String> subjects(List<Finding> findings) {
        return findings.stream().map(Finding::subject).toList();
    }

    /** Every proving chain the findings carry, which is none at all unless the run proved reachability. */
    private static List<String> chains(List<Finding> findings) {
        return findings.stream()
                .flatMap(finding -> EngineFixture.evidence(finding).stream())
                .filter(line -> line.startsWith("reachable via: "))
                .toList();
    }
}
