package sh.zolt.jarproof.fixtures.missingfield;

/**
 * The budget field is a non-final {@code public static int}, so javac emits a real getstatic
 * instead of folding the value into the caller's constant pool.
 */
public final class RetryBudget {
    public static int maximumAttempts = 5;

    public int floor() {
        return 1;
    }
}
