package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;

/**
 * Everything one run knows about a classpath after reading it, and the one place to ask questions of.
 *
 * <p>Both indexes are built once. {@code declarations} maps a class internal name to every artifact
 * that claims it, in classpath order, so the first element is the copy the runtime reaches and the
 * rest are shadowed. {@code artifactsByPackage} maps a package internal prefix to the artifacts
 * contributing to it, again in classpath order. Keys are sorted, so iteration is stable and reports
 * built from it are reproducible.
 */
final class ArtifactCatalog {
    private final EffectiveClasspath classpath;
    private final List<IndexedArtifact> artifacts;
    private final Map<String, List<ClassDeclaration>> declarations;
    private final Map<String, Set<String>> artifactsByPackage;

    private ArtifactCatalog(EffectiveClasspath classpath, List<IndexedArtifact> artifacts) {
        this.classpath = classpath;
        this.artifacts = List.copyOf(artifacts);
        Map<String, List<ClassDeclaration>> claimed = new TreeMap<>();
        Map<String, Set<String>> contributors = new TreeMap<>();
        for (IndexedArtifact artifact : this.artifacts) {
            for (IndexedClass declared : artifact.classes()) {
                claimed.computeIfAbsent(declared.internalName(), name -> new ArrayList<>())
                        .add(new ClassDeclaration(artifact.entry(), declared));
                ArchiveLayout.packageOf(declared.internalName()).ifPresent(name -> contributors
                        .computeIfAbsent(name, absent -> new LinkedHashSet<>())
                        .add(artifact.entry().display()));
            }
        }
        this.declarations = Collections.unmodifiableMap(claimed);
        this.artifactsByPackage = Collections.unmodifiableMap(contributors);
    }

    /**
     * Reads every entry of an effective classpath.
     *
     * @param classpath the assembled classpath
     * @param runtime the target runtime, which decides multi-release selection
     * @param budget the run's resource budget
     * @return the catalog
     */
    static ArtifactCatalog of(EffectiveClasspath classpath, TargetRuntime runtime, ResourceBudget budget) {
        List<IndexedArtifact> read = new ArrayList<>();
        for (ClasspathEntry entry : classpath.entries()) {
            read.add(entry.kind() == EntryKind.DIRECTORY
                    ? DirectoryArtifactReader.read(entry, budget)
                    : ArchiveArtifactReader.read(entry, budget, runtime.javaRelease()));
        }
        return new ArtifactCatalog(classpath, read);
    }

    /** Reads the whole classpath of one request. */
    static ArtifactCatalog read(VerificationRequest request, ResourceBudget budget) {
        return of(ClasspathExpander.expand(request), request.targetRuntime(), budget);
    }

    /** Returns the indexed artifacts in classpath order. */
    List<IndexedArtifact> artifacts() {
        return artifacts;
    }

    /** Returns the classpath these artifacts came from. */
    EffectiveClasspath classpath() {
        return classpath;
    }

    /** Returns every claim on every class internal name, keyed by that name. */
    Map<String, List<ClassDeclaration>> declarations() {
        return declarations;
    }

    /** Returns the artifacts contributing to each package internal prefix. */
    Map<String, Set<String>> artifactsByPackage() {
        return artifactsByPackage;
    }

    /** Returns the declaration the target runtime would reach for one class internal name. */
    Optional<ClassDeclaration> winner(String internalName) {
        return declarations.getOrDefault(internalName, List.of()).stream().findFirst();
    }

    /** Returns what assembling and reading the classpath already proved. */
    List<Finding> findings() {
        List<Finding> found = new ArrayList<>(classpath.findings());
        artifacts.forEach(artifact -> found.addAll(artifact.findings()));
        return List.copyOf(found);
    }
}
