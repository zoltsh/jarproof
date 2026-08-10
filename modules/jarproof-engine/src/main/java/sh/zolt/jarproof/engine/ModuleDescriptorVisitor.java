package sh.zolt.jarproof.engine;

import java.util.Optional;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Turns one descriptor class file into the module it declares.
 *
 * <p>A descriptor is an ordinary class file with a module attribute instead of members, so it is read
 * the same way every other class file in the engine is: bytes in, no resolution, no execution, and no
 * question asked of the machine this runs on.
 *
 * <p>Two inputs produce no descriptor at all rather than a failure. Bytes no parser accepts are
 * already reported as a corrupt entry by the artifact reader, and repeating that here would point a
 * reader at the wrong file. A class file that merely happens to be named like a descriptor but carries
 * no module attribute declares no module, so the artifact holding it is making no module claim.
 */
final class ModuleDescriptorVisitor extends ClassVisitor {
    private final String entryName;
    private final ModuleClauseVisitor clauses = new ModuleClauseVisitor();
    private Optional<String> moduleName = Optional.empty();

    private ModuleDescriptorVisitor(String entryName) {
        super(Opcodes.ASM9);
        this.entryName = entryName;
    }

    /**
     * Reads one descriptor class file.
     *
     * @param classFile the complete class file bytes
     * @param entryName the entry the bytes came from, which a diagnostic points at
     * @return the descriptor, or empty when the bytes declare no module
     */
    static Optional<ModuleDescriptor> parse(byte[] classFile, String entryName) {
        ModuleDescriptorVisitor visitor = new ModuleDescriptorVisitor(entryName);
        try {
            new ClassReader(classFile).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
        return visitor.declared();
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        moduleName = Optional.of(name);
        return clauses;
    }

    private Optional<ModuleDescriptor> declared() {
        return moduleName.map(name -> clauses.descriptor(entryName, name));
    }
}
