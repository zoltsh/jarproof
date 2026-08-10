package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.Finding;

/**
 * The classpath the target runtime would actually search, in search order.
 *
 * <p>Application roots come first in supplied order, then explicit classpath entries, with each
 * JAR's manifest chain inserted immediately after it. Every entry appears once: the first
 * normalized path wins, so a repeated entry keeps its earliest position. {@code findings} carries
 * what assembling the classpath already proved, before any class file was read.
 */
record EffectiveClasspath(List<ClasspathEntry> entries, List<Finding> findings) {
    EffectiveClasspath {
        entries = List.copyOf(entries);
        findings = List.copyOf(findings);
    }
}
