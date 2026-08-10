package sh.zolt.jarproof.engine;

import java.util.Objects;

/** One catalog row: the platform module that owns a class, plus that class's declared shape. */
record JdkSymbolEntry(String module, ClassShape shape) {
    JdkSymbolEntry {
        Objects.requireNonNull(module);
        Objects.requireNonNull(shape);
    }
}
