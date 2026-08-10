package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

/** Accumulates the bytecode-triggered references of one class while its bytes stream past. */
final class ReferenceCollector {
    private static final int MAXIMUM_CONSTANT_DEPTH = 8;
    private static final char ARRAY_DESCRIPTOR_START = '[';

    private final List<TypeReference> types = new ArrayList<>();
    private final List<MemberReference> members = new ArrayList<>();

    /** Records a class named by an instruction, as an internal name or an array descriptor. */
    void addInternalName(String type, String referencingMethod) {
        Type parsed = type.charAt(0) == ARRAY_DESCRIPTOR_START ? Type.getType(type) : Type.getObjectType(type);
        addType(parsed, referencingMethod);
    }

    /** Records a class named by a type descriptor. */
    void addDescriptor(String descriptor, String referencingMethod) {
        addType(Type.getType(descriptor), referencingMethod);
    }

    /** Records a member named by an instruction. */
    void addMember(ReferenceKind kind, String owner, String name, String descriptor, String referencingMethod) {
        members.add(new MemberReference(kind, owner, name, descriptor, referencingMethod));
    }

    /** Records the member a constant-pool method handle names. */
    void addHandle(Handle handle, String referencingMethod) {
        members.add(new MemberReference(
                ReferenceKind.METHOD_HANDLE,
                handle.getOwner(),
                handle.getName(),
                handle.getDesc(),
                referencingMethod));
    }

    /** Records whatever a loadable constant reaches, following dynamic constants to their roots. */
    void addConstant(Object value, String referencingMethod) {
        addConstant(value, referencingMethod, 0);
    }

    /** Returns the references gathered so far. */
    ClassReferences references() {
        return new ClassReferences(types, members);
    }

    private void addConstant(Object value, String referencingMethod, int depth) {
        if (depth > MAXIMUM_CONSTANT_DEPTH) {
            return;
        }
        if (value instanceof Type type) {
            addType(type, referencingMethod);
        } else if (value instanceof Handle handle) {
            addHandle(handle, referencingMethod);
        } else if (value instanceof ConstantDynamic dynamic) {
            addDynamic(dynamic, referencingMethod, depth);
        }
    }

    private void addDynamic(ConstantDynamic dynamic, String referencingMethod, int depth) {
        addHandle(dynamic.getBootstrapMethod(), referencingMethod);
        for (int argument = 0; argument < dynamic.getBootstrapMethodArgumentCount(); argument++) {
            addConstant(dynamic.getBootstrapMethodArgument(argument), referencingMethod, depth + 1);
        }
    }

    private void addType(Type type, String referencingMethod) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (element.getSort() == Type.OBJECT) {
            types.add(new TypeReference(element.getInternalName(), referencingMethod));
        }
    }
}
