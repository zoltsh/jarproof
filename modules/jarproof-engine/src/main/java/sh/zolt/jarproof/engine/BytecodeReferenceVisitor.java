package sh.zolt.jarproof.engine;

import java.util.Optional;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.engine.ReferenceCollector.CallSite;

/**
 * Streams one method body and hands every reference the JVM would resolve to the collector.
 *
 * <p>Only instruction-triggered references are reported: type and member instructions, catch types,
 * loadable constants, and the bootstrap method and arguments of a dynamic call site, followed
 * recursively. Stack map frames are skipped before this visitor ever sees them.
 *
 * <p>Debug information is read for exactly one purpose. The line table is a sequence of markers, so
 * the line in force is the last one announced, and every reference after it is recorded at that
 * line; a method whose class was compiled without line numbers records no line at all rather than
 * borrowing one. Local variable names, parameter names, and the source debug extension are visited
 * and dropped, because none of them describes a reference the JVM resolves. The line belongs to this
 * visitor rather than to the collector because a fresh visitor is created per method, which is
 * exactly the scope a line table has.
 */
final class BytecodeReferenceVisitor extends MethodVisitor {
    private final ReferenceCollector collector;
    private final String referencingMethod;
    private Optional<Integer> line = Optional.empty();

    BytecodeReferenceVisitor(ReferenceCollector collector, String referencingMethod) {
        super(Opcodes.ASM9);
        this.collector = collector;
        this.referencingMethod = referencingMethod;
    }

    @Override
    public void visitLineNumber(int number, Label start) {
        line = Optional.of(number);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        collector.addInternalName(type, site());
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
        collector.addDescriptor(descriptor, site());
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        collector.addMember(ReferenceKind.forOpcode(opcode), owner, name, descriptor, site());
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean ownedByInterface) {
        collector.addMember(ReferenceKind.forOpcode(opcode), owner, name, descriptor, site());
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null) {
            collector.addInternalName(type, site());
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        collector.addConstant(value, site());
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
        collector.addHandle(bootstrap, site());
        for (Object argument : arguments) {
            collector.addConstant(argument, site());
        }
    }

    private CallSite site() {
        return new CallSite(referencingMethod, line);
    }
}
