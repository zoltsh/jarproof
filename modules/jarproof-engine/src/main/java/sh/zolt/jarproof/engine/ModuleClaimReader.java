package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads what every artifact on the classpath claims about the module path, in classpath order.
 *
 * <p>The descriptor entry is not searched for: the artifact index already selected which entry of this
 * artifact the target runtime would read, multi-release archives included, and the descriptor is
 * simply the class it indexed under the descriptor's own internal name. Its bytes are then read a
 * second time, because the index keeps a digest and a shape rather than the bytes, and a module
 * attribute is neither. Reading them here rather than widening the shared index is deliberate: every
 * other check would carry the cost of a structure only this one reads.
 *
 * <p>Archives are opened once for both facts, closed on every path out, and never extracted, exactly
 * as the class reader does. The bytes come out of a budget of their own, because the entry point this
 * check offers a pipeline takes no budget to charge; the per-entry ceilings that keep a crafted
 * archive from being expanded still apply to every read.
 *
 * <p>A class directory is read for a descriptor but never for a manifest, which is the same rule the
 * directory reader already applies: the runtime addresses a directory by path and honours no manifest
 * inside it, so an exploded module can claim a name only by carrying a descriptor.
 */
final class ModuleClaimReader {
    /** Internal name of the class file a modular artifact keeps its descriptor in. */
    static final String DESCRIPTOR_NAME = "module-info";

    private static final Attributes.Name AUTOMATIC_NAME = new Attributes.Name("Automatic-Module-Name");

    private final ResourceBudget budget = new ResourceBudget();
    private final List<ModuleClaim> claims = new ArrayList<>();

    private ModuleClaimReader() {
    }

    /**
     * Reads every artifact's module claim.
     *
     * @param catalog the read classpath
     * @return one claim per artifact, in classpath order
     */
    static List<ModuleClaim> read(ArtifactCatalog catalog) {
        ModuleClaimReader reader = new ModuleClaimReader();
        catalog.artifacts().forEach(reader::add);
        return List.copyOf(reader.claims);
    }

    /**
     * A nested position never makes a module claim: the module path takes whole files, a nested
     * library is reachable only through its launcher, and the outer archive's manifest belongs to
     * the host position alone — reading it for every position would hand one Automatic-Module-Name
     * to three artifacts at once.
     */
    private void add(IndexedArtifact artifact) {
        EntryKind stored = artifact.entry().kind();
        if (stored == EntryKind.NESTED_CLASSES || stored == EntryKind.NESTED_ARCHIVE) {
            claims.add(new ModuleClaim(artifact, Optional.empty(), Optional.empty()));
            return;
        }
        Optional<String> entryName = descriptorEntry(artifact);
        if (stored == EntryKind.DIRECTORY) {
            claims.add(new ModuleClaim(artifact, Optional.empty(), fromDirectory(artifact, entryName)));
            return;
        }
        addArchive(artifact, entryName);
    }

    private void addArchive(IndexedArtifact artifact, Optional<String> entryName) {
        try (ZipFile archive = new ZipFile(artifact.entry().path().toFile())) {
            Optional<Manifest> manifest = ArchiveManifest.of(archive);
            Optional<ModuleDescriptor> descriptor = Optional.empty();
            if (entryName.isPresent()) {
                descriptor = ModuleDescriptorVisitor.parse(content(archive, entryName.get()), entryName.get());
            }
            claims.add(new ModuleClaim(
                    artifact, ArchiveManifest.mainAttribute(manifest, AUTOMATIC_NAME), descriptor));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Optional<ModuleDescriptor> fromDirectory(IndexedArtifact artifact, Optional<String> entryName) {
        return entryName.flatMap(
                name -> ModuleDescriptorVisitor.parse(content(artifact.entry().path(), name), name));
    }

    private static Optional<String> descriptorEntry(IndexedArtifact artifact) {
        return artifact.classes().stream()
                .filter(declared -> declared.internalName().equals(DESCRIPTOR_NAME))
                .map(IndexedClass::entryName)
                .findFirst();
    }

    private byte[] content(ZipFile archive, String entryName) throws IOException {
        ZipEntry selected = archive.getEntry(entryName);
        budget.checkClassFileBytes(selected.getSize(), entryName);
        budget.checkCompressionRatio(selected.getCompressedSize(), selected.getSize(), entryName);
        try (InputStream bytes = archive.getInputStream(selected)) {
            byte[] descriptor = bytes.readNBytes(ResourceBudget.MAXIMUM_CLASS_FILE_BYTES + 1);
            budget.checkClassFileBytes(descriptor.length, entryName);
            budget.addExpandedBytes(descriptor.length, entryName);
            return descriptor;
        }
    }

    private byte[] content(Path directory, String entryName) {
        Path file = directory.resolve(entryName);
        try {
            budget.checkClassFileBytes(Files.size(file), entryName);
            byte[] descriptor = Files.readAllBytes(file);
            budget.addExpandedBytes(descriptor.length, entryName);
            return descriptor;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
