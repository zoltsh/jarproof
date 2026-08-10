package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationRequest;

/**
 * Assembles the classpath the target runtime would search, following launcher rules.
 *
 * <p>Application roots come first in supplied order, then the supplied classpath entries. A
 * wildcard entry expands to the archives directly inside its directory, sorted by file name; the
 * java launcher leaves that order unspecified, so the expansion is recorded as one group and the
 * invented order is never presented as a guarantee. Every JAR's manifest {@code Class-Path} is
 * resolved beside that JAR and inserted immediately after it, recursively, and manifest wildcards
 * are left alone because the launcher does not expand them either. An entry that is already on the
 * path keeps its earliest position: identity is the normalized absolute path, so the first
 * appearance wins.
 */
final class ClasspathExpander {
    private final List<ClasspathEntry> entries = new ArrayList<>();
    private final Set<Path> visited = new LinkedHashSet<>();
    private final List<Finding> findings = new ArrayList<>();

    private ClasspathExpander() {
    }

    /**
     * Builds the effective classpath for one request.
     *
     * @param request the verification request
     * @return the ordered classpath and what assembling it proved
     * @throws IllegalArgumentException when a supplied entry does not exist or cannot be read
     */
    static EffectiveClasspath expand(VerificationRequest request) {
        ClasspathExpander expander = new ClasspathExpander();
        request.applications().forEach(expander::addApplication);
        request.classpath().forEach(expander::addSupplied);
        return new EffectiveClasspath(expander.entries, expander.findings);
    }

    private void addApplication(Path application) {
        if (!Files.isReadable(application)) {
            throw new IllegalArgumentException(
                    "This application artifact does not exist or cannot be read: " + application);
        }
        add(application.toString(), application, ClasspathOrigin.APPLICATION, Optional.empty());
    }

    private void addSupplied(Path supplied) {
        Path name = supplied.getFileName();
        if (name != null && ArchiveLayout.WILDCARD_NAME.equals(name.toString())) {
            addWildcard(supplied);
            return;
        }
        if (!Files.isReadable(supplied)) {
            throw new IllegalArgumentException("This classpath entry does not exist or cannot be read: " + supplied);
        }
        add(supplied.toString(), supplied, ClasspathOrigin.CLASSPATH, Optional.empty());
    }

    private void addWildcard(Path wildcard) {
        Path parent = wildcard.getParent();
        Path directory = parent == null ? Path.of("") : parent;
        if (!Files.isDirectory(directory.toAbsolutePath())) {
            throw new IllegalArgumentException("This classpath wildcard needs an existing directory: " + wildcard);
        }
        Optional<String> source = Optional.of(wildcard.toString());
        for (String archive : archiveNamesIn(directory)) {
            Path expanded = directory.resolve(archive);
            add(expanded.toString(), expanded, ClasspathOrigin.CLASSPATH, source);
        }
    }

    private static List<String> archiveNamesIn(Path directory) {
        try (Stream<Path> children = Files.list(directory.toAbsolutePath())) {
            return children.filter(Files::isRegularFile)
                    .map(child -> child.getFileName().toString())
                    .filter(ArchiveLayout::isExpandableArchive)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void add(String display, Path path, ClasspathOrigin origin, Optional<String> wildcardSource) {
        if (!visited.add(path.toAbsolutePath().normalize())) {
            return;
        }
        EntryKind kind = Files.isDirectory(path) ? EntryKind.DIRECTORY : EntryKind.ARCHIVE;
        ClasspathEntry entry = new ClasspathEntry(display, path, kind, origin, wildcardSource);
        entries.add(entry);
        if (kind == EntryKind.ARCHIVE) {
            addManifestChain(entry);
        }
    }

    private void addManifestChain(ClasspathEntry declaring) {
        List<String> declared = ArchiveManifest.classPath(ArchiveManifest.read(declaring.path()));
        if (declared.isEmpty()) {
            return;
        }
        Path parent = declaring.path().getParent();
        Path directory = parent == null ? Path.of("") : parent;
        for (String candidate : declared) {
            addInherited(declaring, directory, candidate);
        }
    }

    private void addInherited(ClasspathEntry declaring, Path directory, String candidate) {
        Optional<Path> resolved = resolve(directory, candidate);
        if (resolved.filter(Files::isReadable).isEmpty()) {
            findings.add(ManifestClassPathFinding.of(
                    declaring, candidate, resolved.map(Path::toString).orElse(candidate)));
            return;
        }
        add(resolved.get().toString(), resolved.get(), ClasspathOrigin.CLASSPATH, Optional.empty());
    }

    private static Optional<Path> resolve(Path directory, String candidate) {
        try {
            return Optional.of(directory.resolve(candidate));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }
}
