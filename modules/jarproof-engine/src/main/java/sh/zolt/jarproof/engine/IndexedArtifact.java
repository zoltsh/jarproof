package sh.zolt.jarproof.engine;

import java.util.List;
import java.util.Optional;
import sh.zolt.jarproof.api.Finding;

/**
 * One classpath entry after it has been read: the classes it presents to the target runtime, the
 * identity it claims, the packages it seals, and what reading it already proved.
 *
 * <p>Classes are ordered by their base entry name, so two runs over the same artifact produce the
 * same list. A class directory claims no identity and seals nothing, because the runtime honours
 * neither for a directory.
 */
record IndexedArtifact(
        ClasspathEntry entry,
        List<IndexedClass> classes,
        Optional<ArtifactCoordinate> coordinate,
        List<String> sealedPackages,
        List<Finding> findings) {
    IndexedArtifact {
        classes = List.copyOf(classes);
        sealedPackages = List.copyOf(sealedPackages);
        findings = List.copyOf(findings);
    }
}
