package sh.zolt.jarproof.fixtures.classkind;

/** Version 1 is a class, so callers emit invokevirtual against a class reference. */
public class Renderer {
    public String render() {
        return "class renderer";
    }
}
