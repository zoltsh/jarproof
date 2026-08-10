package sh.zolt.jarproof.fixtures.missingfield;

/** Version 2 removes the static field, so a v1 getstatic hits NoSuchFieldError. */
public final class RetryBudget {
    public int floor() {
        return 1;
    }
}
