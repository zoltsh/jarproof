package sh.zolt.jarproof.architecture;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

record JavaSourceUnit(
        Path path,
        String packageName,
        List<String> imports,
        Set<String> qualifiedReferences,
        List<String> stringLiterals,
        List<JavaTypeShape> types,
        List<JavaMethodShape> methods) {
}
