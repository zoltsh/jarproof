package sh.zolt.jarproof.engine;

import java.util.Map;
import org.objectweb.asm.Opcodes;

/**
 * How one bytecode instruction reaches a member, which decides what the JVM checks when it links.
 *
 * <p>The four field kinds and four invocation kinds are modelled separately because a static
 * reference to an instance member, or an interface invocation of a class member, is a different
 * failure from a member that is simply absent. {@link #METHOD_HANDLE} covers a member named by a
 * constant-pool method handle rather than by an instruction: a bootstrap method, a bootstrap
 * argument, or a handle pushed directly. Those are reachable but not necessarily executed, so they
 * deserve softer treatment than a call site.
 */
enum ReferenceKind {
    /** Reads a static field. */
    GET_STATIC,

    /** Writes a static field. */
    PUT_STATIC,

    /** Reads an instance field. */
    GET_FIELD,

    /** Writes an instance field. */
    PUT_FIELD,

    /** Invokes an instance method through its class. */
    INVOKE_VIRTUAL,

    /** Invokes a constructor, a private method, or a superclass method. */
    INVOKE_SPECIAL,

    /** Invokes a static method. */
    INVOKE_STATIC,

    /** Invokes an instance method through an interface. */
    INVOKE_INTERFACE,

    /** Names a member through a constant-pool method handle. */
    METHOD_HANDLE;

    private static final Map<Integer, ReferenceKind> BY_OPCODE = Map.of(
            Opcodes.GETSTATIC, GET_STATIC,
            Opcodes.PUTSTATIC, PUT_STATIC,
            Opcodes.GETFIELD, GET_FIELD,
            Opcodes.PUTFIELD, PUT_FIELD,
            Opcodes.INVOKEVIRTUAL, INVOKE_VIRTUAL,
            Opcodes.INVOKESPECIAL, INVOKE_SPECIAL,
            Opcodes.INVOKESTATIC, INVOKE_STATIC,
            Opcodes.INVOKEINTERFACE, INVOKE_INTERFACE);

    /**
     * Maps a field or invocation opcode to its reference kind.
     *
     * @param opcode a field or invocation opcode
     * @return the matching kind
     * @throws IllegalStateException when the opcode names no member
     */
    static ReferenceKind forOpcode(int opcode) {
        ReferenceKind kind = BY_OPCODE.get(opcode);
        if (kind == null) {
            throw new IllegalStateException("This bytecode opcode names no member: " + opcode);
        }
        return kind;
    }
}
