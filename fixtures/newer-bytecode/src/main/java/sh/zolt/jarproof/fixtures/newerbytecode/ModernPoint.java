package sh.zolt.jarproof.fixtures.newerbytecode;

/** Compiled at release 21 so a check against an older target runtime reports JP3001. */
public final class ModernPoint {
    private final int magnitude;

    public ModernPoint(int magnitude) {
        this.magnitude = magnitude;
    }

    public int magnitude() {
        return magnitude;
    }
}
