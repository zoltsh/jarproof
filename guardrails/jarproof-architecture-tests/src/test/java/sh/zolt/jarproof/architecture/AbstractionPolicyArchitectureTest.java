package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;

final class AbstractionPolicyArchitectureTest {
    private static final Set<String> APPROVED_INTERFACES = Set.of();
    private static final Set<String> APPROVED_ABSTRACT_TYPES = Set.of();

    @Test
    void everyInterfaceRepresentsAnExplicitlyReviewedSeam() {
        Set<String> interfaces = JavaSourceParser.productionSources().stream()
                .flatMap(source -> source.types().stream())
                .filter(type -> type.kind().equals("INTERFACE"))
                .map(JavaTypeShape::qualifiedName)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(APPROVED_INTERFACES, interfaces,
                "Add only interfaces that represent a concrete contract or capability seam");
    }

    @Test
    void everyAbstractClassHasAnExplicitReasonToExist() {
        Set<String> abstractTypes = JavaSourceParser.productionSources().stream()
                .flatMap(source -> source.types().stream())
                .filter(type -> type.modifiers().contains(Modifier.ABSTRACT))
                .map(JavaTypeShape::qualifiedName)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(APPROVED_ABSTRACT_TYPES, abstractTypes,
                "Prefer concrete package-private types unless inheritance models a real contract");
    }
}
