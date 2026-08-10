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
 * appearance wins. That same normalized absolute path is the handle every later read of the entry
 * opens, while every report of it keeps the caller's own text.
 *
 * <p>An application root that carries its own dependencies expands into the several positions its
 * launcher would search instead of the single position it occupies on disk — see
 * {@link NestedApplication} for the order and for why only an application root is expanded that way.
 */
final class ClasspathExpander {
    private final List<ClasspathEntry> entries = new ArrayList<>();
    private final Set<Path> visited = new LinkedHashSet<>();
    private final List<Finding> findings = new ArrayList<>();
    private final ResourceBudget budget;

    private ClasspathExpander(ResourceBudget budget) {
        this.budget = budget;
    }

    /**
     * Builds the effective classpath for one request.
     *
     * @param request the verification request
     * @param budget the run's resource budget, which bounds reading a nested layout
     * @return the ordered classpath and what assembling it proved
     * @throws IllegalArgumentException when a supplied entry does not exist or cannot be read
     */
    static EffectiveClasspath expand(VerificationRequest request, ResourceBudget budget) {
        ClasspathExpander expander = new ClasspathExpander(budget);
        request.applications().forEach(expander::addApplication);
        request.classpath().forEach(expander::addSupplied);
        return new EffectiveClasspath(expander.entries, expander.findings);
    }

    private void addApplication(Path application) {
        Path handle = readHandle(application);
        if (!Files.isReadable(handle)) {
            throw new IllegalArgumentException(
                    "This application artifact does not exist or cannot be read: " + application);
        }
        Optional<NestedApplication> carried = carriedDependencies(handle, application.toString());
        if (carried.isEmpty()) {
            add(application.toString(), application, ClasspathOrigin.APPLICATION, Optional.empty());
            return;
        }
        if (visited.add(handle)) {
            findings.addAll(carried.get().findings());
            carried.get().entries().forEach(this::record);
        }
    }

    private Optional<NestedApplication> carriedDependencies(Path handle, String display) {
        return Files.isDirectory(handle)
                ? Optional.empty()
                : NestedApplication.of(handle, display, budget);
    }

    private void addSupplied(Path supplied) {
        Path name = supplied.getFileName();
        if (name != null && ArchiveLayout.WILDCARD_NAME.equals(name.toString())) {
            addWildcard(supplied);
            return;
        }
        if (!Files.isReadable(readHandle(supplied))) {
            throw new IllegalArgumentException("This classpath entry does not exist or cannot be read: " + supplied);
        }
        add(supplied.toString(), supplied, ClasspathOrigin.CLASSPATH, Optional.empty());
    }

    private void addWildcard(Path wildcard) {
        Path directory = parentOf(wildcard);
        Path handle = readHandle(directory);
        if (!Files.isDirectory(handle)) {
            throw new IllegalArgumentException("This classpath wildcard needs an existing directory: " + wildcard);
        }
        Optional<String> source = Optional.of(wildcard.toString());
        for (String archive : archiveNamesIn(handle)) {
            Path expanded = directory.resolve(archive);
            add(expanded.toString(), expanded, ClasspathOrigin.CLASSPATH, source);
        }
    }

    private static List<String> archiveNamesIn(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isRegularFile)
                    .map(child -> child.getFileName().toString())
                    .filter(ArchiveLayout::isExpandableArchive)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void add(String display, Path supplied, ClasspathOrigin origin, Optional<String> wildcardSource) {
        Path handle = readHandle(supplied);
        if (!visited.add(handle)) {
            return;
        }
        EntryKind kind = Files.isDirectory(handle) ? EntryKind.DIRECTORY : EntryKind.ARCHIVE;
        record(new ClasspathEntry(display, handle, kind, origin, wildcardSource, Optional.empty()));
    }

    /**
     * Records one settled position and chains from it. A manifest {@code Class-Path} is followed from
     * whichever position occupies the archive itself, so an expanded application archive chains from
     * its own top level rather than from a position derived inside it.
     */
    private void record(ClasspathEntry entry) {
        entries.add(entry);
        if (entry.kind() == EntryKind.ARCHIVE || entry.kind() == EntryKind.HOST_ARCHIVE) {
            addManifestChain(entry);
        }
    }

    /**
     * Resolves one supplied entry, once, to the absolute normalized handle every later read opens.
     *
     * <p>Jarproof resolves a relative entry the way {@code java.nio.file} does — against the
     * {@code user.dir} system property, which is the base {@link Path#toAbsolutePath()} applies —
     * because the engine's public surface takes a {@link Path} and a library that accepts one is
     * expected to honour that API's own semantics; deciding it here once is what stops a later read
     * through {@code java.io}, which resolves a relative name against the process working directory
     * instead, from reaching a different file than the one this entry was validated against.
     */
    private static Path readHandle(Path supplied) {
        return supplied.toAbsolutePath().normalize();
    }

    private void addManifestChain(ClasspathEntry declaring) {
        List<String> declared = ArchiveManifest.classPath(
                ArchiveManifest.read(declaring.path(), declaring.display()));
        if (declared.isEmpty()) {
            return;
        }
        Path beside = parentOf(declaring.path());
        Path besideDisplay = parentOf(Path.of(declaring.display()));
        for (String candidate : declared) {
            addInherited(declaring, beside, besideDisplay, candidate);
        }
    }

    /**
     * Adds one manifest {@code Class-Path} entry, read beside the declaring JAR's handle and reported
     * beside the declaring JAR's own text, so a chained entry renders in the caller's terms too.
     */
    private void addInherited(ClasspathEntry declaring, Path beside, Path besideDisplay, String candidate) {
        Optional<Path> resolved = resolve(beside, candidate).map(ClasspathExpander::readHandle);
        String display = resolve(besideDisplay, candidate).map(Path::toString).orElse(candidate);
        if (resolved.filter(Files::isReadable).isEmpty()) {
            findings.add(ManifestClassPathFinding.of(declaring, candidate, display));
            return;
        }
        add(display, resolved.get(), ClasspathOrigin.CLASSPATH, Optional.empty());
    }

    private static Path parentOf(Path entry) {
        Path parent = entry.getParent();
        return parent == null ? Path.of("") : parent;
    }

    private static Optional<Path> resolve(Path directory, String candidate) {
        try {
            return Optional.of(directory.resolve(candidate));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }
}
