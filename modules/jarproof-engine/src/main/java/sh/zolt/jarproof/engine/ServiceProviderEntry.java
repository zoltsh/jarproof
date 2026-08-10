package sh.zolt.jarproof.engine;

/**
 * One surviving line of a service configuration file: the provider name exactly as it was written,
 * and the one-based number of the physical line it was written on.
 *
 * <p>The text is kept verbatim so a diagnostic can quote what needs editing rather than a cleaned-up
 * version of it, and the line number is kept so the reader is sent to the right line of the right
 * file instead of hunting through a resource with no other landmarks.
 */
record ServiceProviderEntry(String declaredName, int lineNumber) {
    /** Returns whether this line names a class a runtime could ask for. */
    boolean isLegal() {
        return ServiceBinaryName.isLegal(declaredName);
    }

    /** Returns the internal name this provider would be looked up by. */
    String internalName() {
        return ServiceBinaryName.internalName(declaredName);
    }
}
