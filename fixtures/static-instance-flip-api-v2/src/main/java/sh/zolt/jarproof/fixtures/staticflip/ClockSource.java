package sh.zolt.jarproof.fixtures.staticflip;

/**
 * Version 2 keeps the descriptor and drops {@code static}, so a v1 invokestatic resolves to an
 * instance method and the JVM raises IncompatibleClassChangeError.
 */
public final class ClockSource {
    public String label() {
        return "instance clock";
    }
}
