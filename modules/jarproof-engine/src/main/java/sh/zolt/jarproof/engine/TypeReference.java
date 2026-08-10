package sh.zolt.jarproof.engine;

/**
 * One class named by executable bytecode: a type instruction, a catch type, or a constant.
 *
 * <p>Array types are reduced to their element type and primitives are dropped, because only a
 * reference type can be missing. {@code referencingMethod} is the declaring method's name joined to
 * its descriptor, which identifies the call site within its class.
 */
record TypeReference(String internalName, String referencingMethod) {
}
