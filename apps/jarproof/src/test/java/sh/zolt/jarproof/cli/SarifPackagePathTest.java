package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.VerificationResult;

/**
 * Where a SARIF result looks for the source file of a class the default package holds.
 *
 * <p>A candidate source path is the class entry's directory joined to the {@code SourceFile}
 * attribute, and an archive may name an entry with a leading separator -- {@code /Loose.class} is a
 * legal zip entry naming a class in the default package. Its directory is therefore the separator
 * itself, which makes the candidate an absolute path, and an absolute candidate is held by no source
 * root: resolving it against one discards the root entirely and asks about a file at the file system
 * root instead. So the result stays the artifact-level result it would have been without any roots,
 * which is the discipline the whole feature is built on -- an annotation on a line nobody wrote is
 * worse than no annotation.
 *
 * <p>Reading that directory as empty instead is what makes this worth pinning. The candidate would
 * become a bare file name, every root holding a file of that name anywhere at its top level would
 * claim the finding, and a class from one package would be annotated in another's source.
 */
final class SarifPackagePathTest {
    private static final String LOOSE_ENTRY = "/Loose.class";
    private static final String LOOSE_SOURCE = "Loose.java";

    @TempDir
    Path workspace;

    @Test
    void keepsTheArtifactLevelResultForAClassEntryThatOpensWithASeparator() throws IOException {
        VerificationResult result = looseClass();

        assertEquals(
                SarifReport.render(result), SarifReport.render(result, List.of(rootHolding(LOOSE_SOURCE))));
    }

    /**
     * The same root, the same file name, and a class entry naming its package really does map, so the
     * assertion above is about the leading separator and not about a root that holds nothing.
     */
    @Test
    void locatesTheSameFileForAClassEntryThatNamesItsPackage() throws IOException {
        Path root = rootHolding("com/acme/" + LOOSE_SOURCE);

        String document = SarifReport.render(locatedAt("com/acme/Loose.class"), List.of(root));

        assertTrue(document.contains("\"uri\": \"com/acme/" + LOOSE_SOURCE + "\""), document);
    }

    private VerificationResult looseClass() {
        return locatedAt(LOOSE_ENTRY);
    }

    private VerificationResult locatedAt(String classEntry) {
        Finding specimen = SampleFindings.missingMethod();
        return SampleFindings.result(new Finding(
                specimen.code(),
                specimen.severity(),
                specimen.predictedError(),
                ArtifactLocation.ofSource(
                        SampleFindings.APPLICATION, classEntry, Optional.of(LOOSE_SOURCE), Optional.of(7)),
                specimen.subject(),
                specimen.summary(),
                specimen.explanation(),
                specimen.evidence(),
                specimen.remediation()));
    }

    /** A source tree that really holds one file, at the path given. */
    private Path rootHolding(String source) throws IOException {
        Path root = workspace.resolve("src");
        Path file = root.resolve(source);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Loose {}\n");
        return root;
    }
}
