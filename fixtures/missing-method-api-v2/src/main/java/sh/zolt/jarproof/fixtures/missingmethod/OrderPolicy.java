package sh.zolt.jarproof.fixtures.missingmethod;

/** Version 2 drops {@code describe(String, int)}, so a v1 caller hits NoSuchMethodError. */
public final class OrderPolicy {
    public int limit() {
        return 7;
    }
}
