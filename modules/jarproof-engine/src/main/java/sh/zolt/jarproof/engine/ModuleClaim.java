package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * What one classpath artifact says about being deployable on a module path.
 *
 * <p>Two claims count. A descriptor is an explicit one: the artifact names itself, states what it
 * needs, and states what it offers. An {@code Automatic-Module-Name} manifest attribute is a weaker
 * one: the artifact has no descriptor but reserves the name it wants to be read by, which is what
 * lets other descriptors require it. An artifact with neither is a plain classpath library and is
 * outside every JP5xxx check, this release included.
 *
 * <p>The artifact travels whole rather than as a list of names, because two of the checks ask
 * questions only this artifact can answer: whether it declares a class at all, which is what module
 * rules demand of a provider, and whether a package it exposes has anything in it.
 */
record ModuleClaim(
        IndexedArtifact artifact,
        Optional<String> automaticModuleName,
        Optional<ModuleDescriptor> descriptor) {
    /** Returns the artifact path text a report shows, exactly as the caller supplied it. */
    String display() {
        return artifact.entry().display();
    }

    /** Returns whether this artifact advertises module-path deployability at all. */
    boolean isModuleCapable() {
        return descriptor.isPresent() || automaticModuleName.isPresent();
    }

    /** Returns the module name another descriptor could require this artifact by. */
    Optional<String> claimedModuleName() {
        return descriptor.map(ModuleDescriptor::moduleName).or(() -> automaticModuleName);
    }

    /**
     * Returns a class as this artifact itself presents it.
     *
     * @param internalName class internal name
     * @return the class, or empty when this artifact does not declare it, however many other
     *     artifacts do
     */
    Optional<IndexedClass> presented(String internalName) {
        return artifact.classes().stream()
                .filter(declared -> declared.internalName().equals(internalName))
                .findFirst();
    }

    /**
     * Returns whether this artifact declares at least one class in a package.
     *
     * @param packagePrefix package internal prefix
     * @return whether the package has any content here
     */
    boolean contributesTo(String packagePrefix) {
        return artifact.classes().stream()
                .anyMatch(declared -> ArchiveLayout.packageOf(declared.internalName())
                        .filter(packagePrefix::equals)
                        .isPresent());
    }
}
