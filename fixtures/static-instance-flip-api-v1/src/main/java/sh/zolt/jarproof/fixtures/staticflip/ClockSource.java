package sh.zolt.jarproof.fixtures.staticflip;

/** Version 1 declares the label accessor static, so callers emit invokestatic. */
public final class ClockSource {
    public static String label() {
        return "static clock";
    }
}
