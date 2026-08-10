package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/** Proves the resolver stays silent everywhere the JVM links without complaint. */
final class LinkageQuietTest {
    private static final String GREETER = "com/acme/lib/Greeter";
    private static final String LOUD = "com/acme/lib/Loud";
    private static final String LIMITS = "com/acme/lib/Limits";
    private static final String BUDGET = "com/acme/lib/Budget";
    private static final String CLOCK = "com/acme/lib/Clock";
    private static final String BROKEN = "com/acme/lib/Broken";
    private static final String ROWS = "com/acme/app/Rows";
    private static final String METHOD_HANDLE = "java/lang/invoke/MethodHandle";
    private static final String VAR_HANDLE = "java/lang/invoke/VarHandle";
    private static final String ABSTRACT_LIST = "java/util/AbstractList";
    private static final String STRING = "java/lang/String";
    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
    private static final int CONSTANT = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
    private static final int ABSTRACT_METHOD = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;

    @TempDir
    Path workspace;

    @Test
    void staysSilentOnSignaturePolymorphicCallsWhateverDescriptorTheyWrote() {
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, METHOD_HANDLE, "invokeExact",
                        "(Ljava/lang/String;I)Lcom/acme/app/Caller;"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, METHOD_HANDLE, "invoke", "(J)V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, VAR_HANDLE, "compareAndSet",
                        "(Ljava/lang/Object;II)Z"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, VAR_HANDLE, "set",
                        "(Ljava/lang/Object;I)V")));

        assertEquals(List.of(), codes(application, List.of()));
    }

    @Test
    void stillReportsMethodsThatOnlyLookSignaturePolymorphic() {
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, METHOD_HANDLE, "invokeSomehow", "(J)V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, METHOD_HANDLE, "asType", "()V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, METHOD_HANDLE, "invokeWithArguments",
                        "(Ljava/lang/String;)V")));

        assertEquals(List.of("JP1003", "JP1003", "JP1003"), codes(application, List.of()));
    }

    @Test
    void resolvesFieldWritesExactlyAsItResolvesFieldReads() {
        Path library = lib(LinkageFixture.classes(BUDGET, LinkageFixture.type(
                BUDGET,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(
                        LinkageFixture.member("retries", "I", PUBLIC_STATIC),
                        LinkageFixture.member("used", "I", Opcodes.ACC_PUBLIC)))));
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.PUT_STATIC, BUDGET, "retries", "I"),
                LinkageFixture.reference(ReferenceKind.PUT_FIELD, BUDGET, "used", "I")));

        assertEquals(List.of(), codes(application, List.of(library)));
    }

    @Test
    void resolvesAbstractDefaultAndStaticInterfaceMethodsAlike() {
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_INTERFACE, GREETER, "greet", "()V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_INTERFACE, GREETER, "hello", "()V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_SPECIAL, GREETER, "hello", "()V"),
                LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, GREETER, "build", "()V")));

        assertEquals(List.of(), codes(application, List.of(greeter())));
    }

    @Test
    void resolvesAnInterfaceMethodInheritedFromASuperinterface() {
        Path library = lib(LinkageFixture.and(greeterEntries(), LOUD, LinkageFixture.type(
                LOUD, LinkageFixture.PUBLIC_INTERFACE, EngineFixture.OBJECT, List.of(GREETER), List.of())));
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_INTERFACE, LOUD, "greet", "()V")));

        assertEquals(List.of(), codes(application, List.of(library)));
    }

    @Test
    void resolvesObjectMethodsReachedThroughAnInterfaceReceiver() {
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_INTERFACE, GREETER, "toString",
                        "()Ljava/lang/String;"),
                LinkageFixture.reference(ReferenceKind.INVOKE_INTERFACE, GREETER, "hashCode", "()I")));

        assertEquals(List.of(), codes(application, List.of(greeter())));
    }

    @Test
    void resolvesAnInterfaceConstantThroughAnImplementingClass() {
        Path library = lib(LinkageFixture.and(
                LinkageFixture.classes(LIMITS, LinkageFixture.type(
                        LIMITS,
                        LinkageFixture.PUBLIC_INTERFACE,
                        EngineFixture.OBJECT,
                        List.of(),
                        List.of(LinkageFixture.member("MAX", "I", CONSTANT)))),
                BUDGET,
                LinkageFixture.type(BUDGET, LinkageFixture.PUBLIC_CLASS, EngineFixture.OBJECT, List.of(LIMITS),
                        List.of())));
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.GET_STATIC, BUDGET, "MAX", "I")));

        assertEquals(List.of(), codes(application, List.of(library)));
    }

    @Test
    void resolvesMembersInheritedFromARuntimeSuperclassAndItsProtectedState() {
        Path application = app(LinkageFixture.classes(ROWS, LinkageFixture.caller(ROWS, ABSTRACT_LIST, List.of(
                LinkageFixture.reference(ReferenceKind.GET_FIELD, ABSTRACT_LIST, "modCount", "I"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, ROWS, "isEmpty", "()Z"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, ROWS, "size", "()I"),
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, ROWS, "iterator",
                        "()Ljava/util/Iterator;")))));

        assertEquals(List.of(), codes(application, List.of()));
    }

    @Test
    void letsTheRuntimeWinOverAClasspathCopyOfItsOwnClass() {
        Path library = lib(LinkageFixture.classes(STRING, LinkageFixture.type(
                STRING, LinkageFixture.PUBLIC_CLASS, EngineFixture.OBJECT, List.of(), List.of())));
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_VIRTUAL, STRING, "length", "()I"),
                LinkageFixture.reference(ReferenceKind.GET_STATIC, STRING, "CASE_INSENSITIVE_ORDER",
                        "Ljava/util/Comparator;")));

        assertEquals(List.of(), codes(application, List.of(library)));
    }

    @Test
    void resolvesALambdaCallSiteAndTheHandleToItsOwnBody() {
        Path application = app(LinkageFixture.classes(
                LinkageFixture.CALLER, LinkageFixture.lambdaCaller(LinkageFixture.CALLER)));

        assertEquals(List.of(), codes(application, List.of()));
    }

    @Test
    void skipsMembersNamedOnAnArrayType() {
        Path application = caller(List.of(LinkageFixture.reference(
                ReferenceKind.INVOKE_VIRTUAL, "[Ljava/lang/Object;", "clone", "()Ljava/lang/Object;")));

        assertEquals(List.of(), codes(application, List.of()));
    }

    @Test
    void checksAConstantHandleForExistenceWithoutJudgingItsReceiver() {
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.METHOD_HANDLE, CLOCK, "now", "()J"),
                LinkageFixture.reference(ReferenceKind.METHOD_HANDLE, CLOCK, "gone", "()J"),
                LinkageFixture.reference(ReferenceKind.METHOD_HANDLE, CLOCK, "missing", "I")));

        assertEquals(List.of("JP1003", "JP1002"), codes(application, List.of(clock())));
    }

    @Test
    void staysSilentAboutAClassWhoseBytesNoParserAccepted() {
        Path library = lib(LinkageFixture.classes(
                BROKEN, Arrays.copyOf(EngineFixture.classFile(BROKEN), 8)));
        Path application = caller(List.of(
                LinkageFixture.reference(ReferenceKind.INVOKE_STATIC, BROKEN, "reset", "()V")));

        assertEquals(List.of(), codes(application, List.of(library)));
    }

    private Path greeter() {
        return lib(greeterEntries());
    }

    private static Map<String, byte[]> greeterEntries() {
        return LinkageFixture.classes(GREETER, LinkageFixture.type(
                GREETER,
                LinkageFixture.PUBLIC_INTERFACE,
                EngineFixture.OBJECT,
                List.of(),
                List.of(
                        LinkageFixture.member("greet", "()V", ABSTRACT_METHOD),
                        LinkageFixture.member("hello", "()V", Opcodes.ACC_PUBLIC),
                        LinkageFixture.member("build", "()V", PUBLIC_STATIC))));
    }

    private Path clock() {
        return lib(LinkageFixture.classes(CLOCK, LinkageFixture.type(
                CLOCK,
                LinkageFixture.PUBLIC_CLASS,
                EngineFixture.OBJECT,
                List.of(),
                List.of(LinkageFixture.member("now", "()J", Opcodes.ACC_PUBLIC)))));
    }

    private Path caller(List<MemberReference> references) {
        return app(LinkageFixture.classes(
                LinkageFixture.CALLER, LinkageFixture.caller(LinkageFixture.CALLER, references)));
    }

    private Path app(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "app.jar", entries);
    }

    private Path lib(Map<String, byte[]> entries) {
        return EngineFixture.jar(workspace, "lib/library-1.0.jar", entries);
    }

    private static List<String> codes(Path application, List<Path> classpath) {
        List<Finding> findings = LinkageFixture.check(List.of(application), classpath, Scope.APPLICATION);
        return EngineFixture.codes(findings);
    }
}
