package sh.zolt.jarproof.engine;

import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Manifest;

/**
 * One position on the effective classpath, in the order the runtime searches it.
 *
 * <p>{@code display} is the path text a report may show. It is derived only from what the caller
 * supplied — a wildcard contributes its own directory text, a manifest chain contributes the
 * declaring JAR's directory text, a position inside an application archive contributes that archive's
 * text and the entry inside it — and is never absolutized, so identical inputs describe themselves
 * identically on every machine. {@code path} is the read handle: the same entry resolved once to an
 * absolute normalized path, which is both what identifies the entry and what every read of it opens,
 * so no two file system APIs can disagree about which file it names. It is never reported.
 *
 * <p>{@code wildcardSource} carries the wildcard text this entry was expanded from, when it was.
 * Entries sharing one wildcard source have no guaranteed relative order at runtime, which is what
 * makes an otherwise ordinary duplicate class unpredictable.
 *
 * <p>{@code nested} carries where inside an application archive this entry lives, when it lives inside
 * one. Several positions then share one read handle — the classes root, each nested library, and the
 * archive's own top level are all read out of the same file — so a nested position is identified by
 * that handle together with this component rather than by the handle alone.
 *
 * <p>{@code manifest} carries the main manifest assembling the classpath already parsed out of this
 * archive. A manifest {@code Class-Path} decides where an entry sits, so it has to be read before any
 * artifact is indexed; carrying the result forward is what keeps the same bytes from being parsed a
 * second time when the entry is read. It is empty for every position that answers to no manifest of its
 * own: a class directory, the classes root of an application archive, and a library nested inside one,
 * whose manifest is its own and is read from its own bytes while it is walked.
 */
record ClasspathEntry(
        String display,
        Path path,
        EntryKind kind,
        ClasspathOrigin origin,
        Optional<String> wildcardSource,
        Optional<NestedPosition> nested,
        Optional<Manifest> manifest) {
    /** One position whose manifest nobody has read, because nothing about it answers to one. */
    ClasspathEntry(
            String display,
            Path path,
            EntryKind kind,
            ClasspathOrigin origin,
            Optional<String> wildcardSource,
            Optional<NestedPosition> nested) {
        this(display, path, kind, origin, wildcardSource, nested, Optional.empty());
    }
}
