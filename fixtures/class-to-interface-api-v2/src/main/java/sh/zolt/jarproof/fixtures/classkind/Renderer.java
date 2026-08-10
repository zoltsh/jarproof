package sh.zolt.jarproof.fixtures.classkind;

/**
 * Version 2 turns the same fully qualified name into an interface, so a v1 invokevirtual against it
 * fails method resolution with IncompatibleClassChangeError.
 */
public interface Renderer {
    String render();
}
