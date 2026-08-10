package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * One class named by executable bytecode: a type instruction, a catch type, or a constant.
 *
 * <p>Array types are reduced to their element type and primitives are dropped, because only a
 * reference type can be missing. {@code referencingMethod} is the declaring method's name joined to
 * its descriptor, which identifies the call site within its class.
 *
 * <p>{@code line} is the source line the naming instruction belongs to, and it is absent whenever
 * the class file cannot answer: compiled without a {@code LineNumberTable}, or named by a
 * constant-pool entry the bytecode reaches outside any line the table covers.
 */
record TypeReference(String internalName, String referencingMethod, Optional<Integer> line) {
}
