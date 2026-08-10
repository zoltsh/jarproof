package sh.zolt.jarproof.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class NamingArchitectureTest {
    private static final Set<String> GENERIC_TYPE_SUFFIXES = Set.of(
            "Base", "Common", "Helper", "Helpers", "Impl", "Manager", "Misc", "Stuff", "Util", "Utils");

    @Test
    void productionTypesHaveSpecificNames() {
        for (JavaSourceUnit source : JavaSourceParser.productionSources()) {
            for (JavaTypeShape type : source.types()) {
                String name = simpleName(type.qualifiedName());
                assertTrue(name.length() > 1, type.qualifiedName());
                for (String suffix : GENERIC_TYPE_SUFFIXES) {
                    assertFalse(name.endsWith(suffix), type.qualifiedName() + " uses generic suffix " + suffix);
                }
            }
        }
    }

    @Test
    void productionCodeContainsNoPlaceholderMarkersOrBroadSuppressions() {
        for (java.nio.file.Path source : RepositoryLayout.productionJavaFiles()) {
            String content = RepositoryLayout.text(source);
            assertFalse(content.contains("TODO"), RepositoryLayout.relative(source));
            assertFalse(content.contains("FIXME"), RepositoryLayout.relative(source));
            assertFalse(content.contains("return null"), RepositoryLayout.relative(source));
            assertFalse(content.contains("@SuppressWarnings(\"all\")"), RepositoryLayout.relative(source));
            assertFalse(content.contains("NOSONAR"), RepositoryLayout.relative(source));
        }
    }

    private static String simpleName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }
}
