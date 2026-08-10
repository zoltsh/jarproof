package sh.zolt.jarproof.engine;

import java.util.List;

/**
 * Everything one class reaches for when its bytecode runs, in the order the bytecode names it.
 *
 * <p>This is deliberately narrower than the constant pool. Annotations, generic signatures, the
 * declared {@code throws} list, and record component metadata are excluded: the JVM never resolves
 * them on the path that throws a linkage error, so treating them as references manufactures
 * failures that cannot happen. Reflective names are excluded for the same reason — they are strings,
 * not references.
 */
record ClassReferences(List<TypeReference> types, List<MemberReference> members) {
    ClassReferences {
        types = List.copyOf(types);
        members = List.copyOf(members);
    }

    /** Returns the references of a class whose bytecode could not be read. */
    static ClassReferences empty() {
        return new ClassReferences(List.of(), List.of());
    }
}
