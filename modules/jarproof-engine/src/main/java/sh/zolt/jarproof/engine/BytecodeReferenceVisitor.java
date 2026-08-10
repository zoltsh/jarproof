package sh.zolt.jarproof.engine;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Streams one method body and hands every reference the JVM would resolve to the collector.
 *
 * <p>Only instruction-triggered references are reported: type and member instructions, catch types,
 * loadable constants, and the bootstrap method and arguments of a dynamic call site, followed
 * recursively. Frames and debug information are skipped before this visitor ever sees them.
 */
final class BytecodeReferenceVisitor extends MethodVisitor {
    private final ReferenceCollector collector;
    private final String referencingMethod;

    BytecodeReferenceVisitor(ReferenceCollector collector, String referencingMethod) {
        super(Opcodes.ASM9);
        this.collector = collector;
        this.referencingMethod = referencingMethod;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        collector.addInternalName(type, referencingMethod);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
        collector.addDescriptor(descriptor, referencingMethod);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        collector.addMember(ReferenceKind.forOpcode(opcode), owner, name, descriptor, referencingMethod);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean ownedByInterface) {
        collector.addMember(ReferenceKind.forOpcode(opcode), owner, name, descriptor, referencingMethod);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null) {
            collector.addInternalName(type, referencingMethod);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        collector.addConstant(value, referencingMethod);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
        collector.addHandle(bootstrap, referencingMethod);
        for (Object argument : arguments) {
            collector.addConstant(argument, referencingMethod);
        }
    }
}
