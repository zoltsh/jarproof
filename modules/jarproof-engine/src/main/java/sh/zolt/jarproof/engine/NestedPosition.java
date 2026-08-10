package sh.zolt.jarproof.engine;

/**
 * Where one classpath entry lives inside the application archive that carries it.
 *
 * <p>{@code path} is the entry name inside that archive: the classes root as a prefix ending in a
 * separator, or one nested library archive's own entry name. {@code outerDisplay} is the caller's text
 * for the archive holding it, which is what a diagnostic about the layout names — a broken nested
 * layout is a fact about the artifact the caller supplied, not about a position jarproof derived
 * inside it.
 */
record NestedPosition(String outerDisplay, String path) {
}
