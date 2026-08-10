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
 *
 * <p>Debug information is read rather than skipped, for the two facts that let a report point a reader
 * at their own code: the {@code SourceFile} attribute of the class, and the line each reference sits
 * on. Stack map frames stay skipped because nothing here verifies types. What this costs is walking
 * the line table of every method and retaining one file name per class and one line per reference;
 * local variable names, parameter names, and the source debug extension are visited and dropped.
 */
final class ClassStructureVisitor extends ClassVisitor {
    private final ReferenceCollector collector = new ReferenceCollector();
    private final List<String> nestMembers = new ArrayList<>();
    private final List<MemberShape> members = new ArrayList<>();
    private Optional<String> nestHost = Optional.empty();
    private Optional<String> sourceFile = Optional.empty();

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
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            return Optional.of(new ClassStructure(
                    visitor.shape(reader), visitor.collector.references(), visitor.sourceFile));
        } catch (RuntimeException exception) {
            // Malformed bytes surface as unchecked failures from deep inside the parser; a corrupt
            // entry is a finding for the caller to report, never a crash for the caller to survive.
            return Optional.empty();
        }
    }

    /**
     * Keeps the source file name and drops the source debug extension, which describes generated
     * code for a debugger and names nothing the JVM resolves.
     */
    @Override
    public void visitSource(String source, String debug) {
        sourceFile = Optional.ofNullable(source);
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
