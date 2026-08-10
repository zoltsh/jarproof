package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Turns one class file into its declared shape and its bytecode-triggered references.
 *
 * <p>The class identity, access flags, superclass, and interfaces come straight off the constant
 * pool header rather than through a visit callback, so nothing has to be reassembled. Nest
 * attributes are captured because access control on modern bytecode depends on them. Fields and
 * methods land in one member list, where a method is the entry whose descriptor opens with a
 * parenthesis.
 */
final class ClassStructureVisitor extends ClassVisitor {
    private final ReferenceCollector collector = new ReferenceCollector();
    private final List<String> nestMembers = new ArrayList<>();
    private final List<MemberShape> members = new ArrayList<>();
    private Optional<String> nestHost = Optional.empty();

    private ClassStructureVisitor() {
        super(Opcodes.ASM9);
    }

    /**
     * Reads one class file.
     *
     * @param classFile the complete class file bytes
     * @return the structure, or empty when no parser can make sense of the bytes
     */
    static Optional<ClassStructure> parse(byte[] classFile) {
        ClassStructureVisitor visitor = new ClassStructureVisitor();
        try {
            ClassReader reader = new ClassReader(classFile);
            reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return Optional.of(new ClassStructure(visitor.shape(reader), visitor.collector.references()));
        } catch (RuntimeException exception) {
            // Malformed bytes surface as unchecked failures from deep inside the parser; a corrupt
            // entry is a finding for the caller to report, never a crash for the caller to survive.
            return Optional.empty();
        }
    }

    @Override
    public void visitNestHost(String host) {
        nestHost = Optional.of(host);
    }

    @Override
    public void visitNestMember(String member) {
        nestMembers.add(member);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        members.add(new MemberShape(name, descriptor, access));
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] thrown) {
        members.add(new MemberShape(name, descriptor, access));
        return new BytecodeReferenceVisitor(collector, name + descriptor);
    }

    private ClassShape shape(ClassReader reader) {
        return new ClassShape(
                reader.getClassName(),
                reader.getAccess(),
                Optional.ofNullable(reader.getSuperName()),
                List.of(reader.getInterfaces()),
                nestHost,
                nestMembers,
                members);
    }
}
