package sh.zolt.jarproof.architecture;

import java.util.Set;
import javax.lang.model.element.Modifier;

record JavaTypeShape(
        String qualifiedName,
        String kind,
        Set<Modifier> modifiers,
        int fieldCount,
        int methodCount) {
}
