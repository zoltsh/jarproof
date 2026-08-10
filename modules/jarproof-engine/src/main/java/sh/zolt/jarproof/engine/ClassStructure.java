package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * What one readable class file declares, paired with what its bytecode reaches for.
 *
 * <p>{@code sourceFile} is the {@code SourceFile} attribute verbatim, which is a bare file name and
 * not a path, and it is absent for a class compiled without debug information.
 */
record ClassStructure(ClassShape shape, ClassReferences references, Optional<String> sourceFile) {
}
