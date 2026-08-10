package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

final class ReferenceKindTest {
    @Test
    void mapsEveryFieldOpcodeToItsOwnKind() {
        assertEquals(ReferenceKind.GET_STATIC, ReferenceKind.forOpcode(Opcodes.GETSTATIC));
        assertEquals(ReferenceKind.PUT_STATIC, ReferenceKind.forOpcode(Opcodes.PUTSTATIC));
        assertEquals(ReferenceKind.GET_FIELD, ReferenceKind.forOpcode(Opcodes.GETFIELD));
        assertEquals(ReferenceKind.PUT_FIELD, ReferenceKind.forOpcode(Opcodes.PUTFIELD));
    }

    @Test
    void mapsEveryInvocationOpcodeToItsOwnKind() {
        assertEquals(ReferenceKind.INVOKE_VIRTUAL, ReferenceKind.forOpcode(Opcodes.INVOKEVIRTUAL));
        assertEquals(ReferenceKind.INVOKE_SPECIAL, ReferenceKind.forOpcode(Opcodes.INVOKESPECIAL));
        assertEquals(ReferenceKind.INVOKE_STATIC, ReferenceKind.forOpcode(Opcodes.INVOKESTATIC));
        assertEquals(ReferenceKind.INVOKE_INTERFACE, ReferenceKind.forOpcode(Opcodes.INVOKEINTERFACE));
    }

    @Test
    void refusesAnOpcodeThatNamesNoMember() {
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> ReferenceKind.forOpcode(Opcodes.NOP));

        assertTrue(failure.getMessage().contains(String.valueOf(Opcodes.NOP)), failure.getMessage());
    }
}
