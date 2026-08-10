package sh.zolt.jarproof.engine;

import org.objectweb.asm.Opcodes;

/**
 * One field or method after resolution: the class that declares it, and what that class declared.
 *
 * <p>The declaring class is rarely the class the reference named. Resolution walks up from the named
 * class, so the answer is usually a superclass or a superinterface — and it is the declaring class,
 * not the named one, that every access rule is measured against.
 */
record ResolvedMember(ResolvedClass owner, MemberShape member) {
    /** Returns whether the declaration belongs to the class rather than to an instance. */
    boolean isStatic() {
        return flagged(Opcodes.ACC_STATIC);
    }

    /** Returns whether every class may reach this declaration. */
    boolean isPublic() {
        return flagged(Opcodes.ACC_PUBLIC);
    }

    /** Returns whether only the declaring class and its nestmates may reach this declaration. */
    boolean isPrivate() {
        return flagged(Opcodes.ACC_PRIVATE);
    }

    /** Returns whether the declaring package and subclasses may reach this declaration. */
    boolean isProtected() {
        return flagged(Opcodes.ACC_PROTECTED);
    }

    private boolean flagged(int mask) {
        return (member.accessFlags() & mask) != 0;
    }
}
