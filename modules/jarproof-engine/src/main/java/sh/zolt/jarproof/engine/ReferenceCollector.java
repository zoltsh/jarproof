package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    void addInternalName(String type, CallSite site) {
        Type parsed = type.charAt(0) == ARRAY_DESCRIPTOR_START ? Type.getType(type) : Type.getObjectType(type);
        addType(parsed, site);
    }

    /** Records a class named by a type descriptor. */
    void addDescriptor(String descriptor, CallSite site) {
        addType(Type.getType(descriptor), site);
    }

    /** Records a member named by an instruction. */
    void addMember(ReferenceKind kind, String owner, String name, String descriptor, CallSite site) {
        members.add(new MemberReference(kind, owner, name, descriptor, site.referencingMethod(), site.line()));
    }

    /** Records the member a constant-pool method handle names. */
    void addHandle(Handle handle, CallSite site) {
        members.add(new MemberReference(
                ReferenceKind.METHOD_HANDLE,
                handle.getOwner(),
                handle.getName(),
                handle.getDesc(),
                site.referencingMethod(),
                site.line()));
    }

    /** Records whatever a loadable constant reaches, following dynamic constants to their roots. */
    void addConstant(Object value, CallSite site) {
        addConstant(value, site, 0);
    }

    /** Returns the references gathered so far. */
    ClassReferences references() {
        return new ClassReferences(types, members);
    }

    private void addConstant(Object value, CallSite site, int depth) {
        if (depth > MAXIMUM_CONSTANT_DEPTH) {
            return;
        }
        if (value instanceof Type type) {
            addType(type, site);
        } else if (value instanceof Handle handle) {
            addHandle(handle, site);
        } else if (value instanceof ConstantDynamic dynamic) {
            addDynamic(dynamic, site, depth);
        }
    }

    private void addDynamic(ConstantDynamic dynamic, CallSite site, int depth) {
        addHandle(dynamic.getBootstrapMethod(), site);
        for (int argument = 0; argument < dynamic.getBootstrapMethodArgumentCount(); argument++) {
            addConstant(dynamic.getBootstrapMethodArgument(argument), site, depth + 1);
        }
    }

    private void addType(Type type, CallSite site) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (element.getSort() == Type.OBJECT) {
            types.add(new TypeReference(element.getInternalName(), site.referencingMethod(), site.line()));
        }
    }

    /**
     * Where the bytecode named a reference: the method it is written in, and the source line in force
     * at that instruction when the class file records line numbers at all.
     *
     * <p>Carrying the two together is what keeps every recording method inside the parameter ceiling,
     * and it keeps the line beside the method it belongs to instead of beside the reference shapes.
     */
    record CallSite(String referencingMethod, Optional<Integer> line) {
    }
}
