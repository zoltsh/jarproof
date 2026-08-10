package sh.zolt.jarproof.architecture;

import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

record JavaMethodShape(
        String owner,
        String name,
        int lineCount,
        int parameterCount,
        int complexity,
        int maximumNesting,
        List<String> parameterTypes,
        Set<Modifier> modifiers) {
}
