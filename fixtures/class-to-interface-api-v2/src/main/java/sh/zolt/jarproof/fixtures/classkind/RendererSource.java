package sh.zolt.jarproof.fixtures.classkind;

/** Same signature as version 1; the returned renderer is now a lambda over the interface. */
public final class RendererSource {
    public static Renderer create() {
        return () -> "interface renderer";
    }
}
