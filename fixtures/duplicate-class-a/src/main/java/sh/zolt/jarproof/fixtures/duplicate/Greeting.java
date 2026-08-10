package sh.zolt.jarproof.fixtures.duplicate;

/** Copy A of a duplicated class: different bodies and one extra method versus copy B. */
public final class Greeting {
    public String text() {
        return "hello from copy a";
    }

    public String salute() {
        return "salute from copy a";
    }
}
