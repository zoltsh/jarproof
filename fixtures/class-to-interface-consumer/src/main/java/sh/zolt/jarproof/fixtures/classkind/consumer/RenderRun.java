package sh.zolt.jarproof.fixtures.classkind.consumer;

import sh.zolt.jarproof.fixtures.classkind.Renderer;
import sh.zolt.jarproof.fixtures.classkind.RendererSource;

/** Invokes the renderer virtually, which is only a legal call shape against version 1. */
public final class RenderRun {
    public static void main(String[] arguments) {
        Renderer renderer = RendererSource.create();
        System.out.println(renderer.render());
    }
}
