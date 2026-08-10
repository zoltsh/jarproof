package sh.zolt.jarproof.engine;

import java.nio.file.Path;
import java.util.Optional;

/**
 * One position on the effective classpath, in the order the runtime searches it.
 *
 * <p>{@code display} is the path text a report may show. It is derived only from what the caller
 * supplied — a wildcard contributes its own directory text, a manifest chain contributes the
 * declaring JAR's directory text — and is never absolutized, so identical inputs describe
 * themselves identically on every machine. {@code path} is the file system handle used to read the
 * entry; only its normalized absolute form is used for identity, never for reporting.
 *
 * <p>{@code wildcardSource} carries the wildcard text this entry was expanded from, when it was.
 * Entries sharing one wildcard source have no guaranteed relative order at runtime, which is what
 * makes an otherwise ordinary duplicate class unpredictable.
 */
record ClasspathEntry(
        String display,
        Path path,
        EntryKind kind,
        ClasspathOrigin origin,
        Optional<String> wildcardSource) {
}
