package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Collects the clauses of one module descriptor as they are reported.
 *
 * <p>A requirement carrying the static-phase flag is the {@code requires static} form, which asks for
 * a module at compile time and tolerates its absence at run time, so it is kept apart from the
 * requirements that must resolve. The transitive flag is deliberately not separated: a transitive
 * requirement still has to resolve, it merely also grants readability onward.
 *
 * <p>An {@code exports} clause and an {@code opens} clause both land in one list of exposed packages.
 * The reader modules a qualified clause names are ignored, because a package that does not exist
 * cannot be exposed to anybody, and the check this feeds asks only whether it exists.
 */
final class ModuleClauseVisitor extends ModuleVisitor {
    private final List<String> required = new ArrayList<>();
    private final List<String> compileOnly = new ArrayList<>();
    private final List<String> exposed = new ArrayList<>();
    private final List<String> used = new ArrayList<>();
    private final List<ModuleProvision> provided = new ArrayList<>();

    ModuleClauseVisitor() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visitRequire(String module, int access, String version) {
        if ((access & Opcodes.ACC_STATIC_PHASE) != 0) {
            compileOnly.add(module);
            return;
        }
        required.add(module);
    }

    @Override
    public void visitExport(String packaze, int access, String... modules) {
        exposed.add(packaze);
    }

    @Override
    public void visitOpen(String packaze, int access, String... modules) {
        exposed.add(packaze);
    }

    @Override
    public void visitUse(String service) {
        used.add(service);
    }

    @Override
    public void visitProvide(String service, String... providers) {
        provided.add(new ModuleProvision(service, List.of(providers)));
    }

    /**
     * Returns everything collected so far as one descriptor.
     *
     * @param entryName the entry the descriptor was read from
     * @param moduleName the module name the descriptor declares
     * @return the descriptor
     */
    ModuleDescriptor descriptor(String entryName, String moduleName) {
        return new ModuleDescriptor(entryName, moduleName, required, compileOnly, exposed, used, provided);
    }
}
