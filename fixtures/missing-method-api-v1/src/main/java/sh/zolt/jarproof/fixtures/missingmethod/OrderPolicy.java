package sh.zolt.jarproof.fixtures.missingmethod;

/** Version 1 of the corpus API: declares the method the consumer compiles against. */
public final class OrderPolicy {
    public String describe(String label, int count) {
        return label + " x" + count;
    }

    public int limit() {
        return 7;
    }
}
