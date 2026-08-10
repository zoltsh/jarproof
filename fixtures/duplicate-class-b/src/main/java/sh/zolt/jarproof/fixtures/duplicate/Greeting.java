package sh.zolt.jarproof.fixtures.duplicate;

/** Copy B of the same fully qualified class: differing bytecode and a smaller method set. */
public final class Greeting {
    public String text() {
        return "hello from copy b";
    }
}
