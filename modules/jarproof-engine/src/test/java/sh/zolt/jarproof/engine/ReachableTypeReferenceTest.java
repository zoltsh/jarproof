package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.Scope;

/**
 * A class a method merely names answers to reachability exactly as a member it calls does.
 *
 * <p>Bytecode reaches for a class two ways: it uses a member of it, or it names the class itself — an
 * allocation, a cast, an array of it, a class constant. The second kind carries no member, so it is
 * resolved along a path of its own, and the question {@link Scope#REACHABLE} asks has to be asked on that
 * path too. A type named in a method nothing calls describes a failure that cannot happen, which is the
 * noise this mode exists to remove, while the wider scope reports it as the library evidence it is.
 */
final class ReachableTypeReferenceTest {
    private static final String MAIN_CLASS = "com/acme/app/Entry";
    private static final String LIBRARY = "com/acme/lib/Shapes";
    private static final String REACHED = "com/acme/gone/Reached";
    private static final String UNREACHED = "com/acme/gone/Unreached";

    @TempDir
    Path workspace;

    @Test
    void omitsATypeNamedOnlyByALibraryMethodNothingCalls() {
        Path application = EngineFixture.jar(workspace, "app.jar", LinkageFixture.classes(
                MAIN_CLASS, ReachableFixture.type(MAIN_CLASS, ReachableFixture.body(
                        ReachableFixture.MAIN, ReachableFixture.call(LIBRARY, ReachableFixture.WORK)))));
        Path library = EngineFixture.jar(
                workspace, "lib/shapes.jar", LinkageFixture.classes(LIBRARY, shapes()));

        List<Finding> reachable = check(application, library, Scope.REACHABLE);
        List<Finding> all = check(application, library, Scope.ALL);

        assertEquals(List.of(REACHED), subjects(reachable));
        assertEquals(List.of(REACHED, UNREACHED), subjects(all).stream().sorted().toList(),
                "the type nobody can execute is still library evidence under the wider scope");
    }

    private static List<Finding> check(Path application, Path library, Scope scope) {
        return LinkageFixture.check(List.of(application), List.of(library), scope);
    }

    /** A class whose reachable method names one absent class and whose unreachable method names another. */
    private static byte[] shapes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, LinkageFixture.PUBLIC_CLASS, LIBRARY, null, EngineFixture.OBJECT, null);
        names(writer, ReachableFixture.WORK, REACHED);
        names(writer, ReachableFixture.UNUSED, UNREACHED);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** One method whose whole body names a class: it allocates, and it calls nothing whatsoever. */
    private static void names(ClassWriter writer, String signature, String named) {
        int split = signature.indexOf('(');
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                signature.substring(0, split),
                signature.substring(split),
                null,
                null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, named);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
    }

    private static List<String> subjects(List<Finding> findings) {
        return findings.stream().map(Finding::subject).toList();
    }
}
