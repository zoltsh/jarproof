package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Extracts the declared shape of one class from its bytes.
 *
 * <p>Identity, access flags, superclass, and interfaces come straight from the constant
 * pool through {@link ClassReader} accessors, so the raw {@code access_flags} of the
 * class file are preserved rather than the synthesized flags a visit callback reports.
 * Members and the nest attributes arrive through the visitor callbacks. Nothing here
 * links, initializes, or otherwise runs the class being described: only bytes are read.
 *
 * <p>Field and method visits deliberately hand back the superclass result, which is the
 * ASM idiom for "record the declaration and skip its attributes"; annotations,
 * generic signatures, and code are never parsed.
 *
 * <p>Member access flags are masked to the sixteen bits a class file actually stores. ASM
 * reports deprecation as an extra flag outside that range because it reads the
 * {@code Deprecated} attribute, and deprecation tells linkage checking nothing.
 */
final class JdkSymbolClassScanner extends ClassVisitor {
    private static final int SHAPE_ONLY =
            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
    private static final Comparator<MemberShape> DECLARATION_ORDER =
            Comparator.comparing(MemberShape::name).thenComparing(MemberShape::descriptor);
    private static final int DECLARED_FLAGS = 0xffff;

    private final List<MemberShape> members = new ArrayList<>();
    private final List<String> nestMembers = new ArrayList<>();
    private Optional<String> nestHost = Optional.empty();

    private JdkSymbolClassScanner() {
        super(Opcodes.ASM9);
    }

    /**
     * Reads one class file into a {@link ClassShape}.
     *
     * @param classFile the complete bytes of a class file, or of a {@code ct.sym} signature
     *     file, which is itself a class file carrying declarations without code
     * @return the declared shape, canonically ordered: members ascend by name then descriptor
     *     and nest members ascend by name, while interfaces keep their declared order because
     *     interface order decides which default method is maximally specific
     */
    static ClassShape shapeOf(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        JdkSymbolClassScanner scanner = new JdkSymbolClassScanner();
        reader.accept(scanner, SHAPE_ONLY);
        scanner.members.sort(DECLARATION_ORDER);
        scanner.nestMembers.sort(Comparator.naturalOrder());
        return new ClassShape(
                reader.getClassName(),
                reader.getAccess(),
                Optional.ofNullable(reader.getSuperName()),
                List.of(reader.getInterfaces()),
                scanner.nestHost,
                scanner.nestMembers,
                scanner.members);
    }

    @Override
    public void visitNestHost(String nestHost) {
        this.nestHost = Optional.of(nestHost);
    }

    @Override
    public void visitNestMember(String nestMember) {
        nestMembers.add(nestMember);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        members.add(new MemberShape(name, descriptor, access & DECLARED_FLAGS));
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        members.add(new MemberShape(name, descriptor, access & DECLARED_FLAGS));
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
