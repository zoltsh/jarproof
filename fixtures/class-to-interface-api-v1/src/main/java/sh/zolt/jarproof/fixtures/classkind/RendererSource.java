package sh.zolt.jarproof.fixtures.classkind;

/** Hands out a renderer so the consumer never has to instantiate one itself. */
public final class RendererSource {
    public static Renderer create() {
        return new Renderer();
    }
}
