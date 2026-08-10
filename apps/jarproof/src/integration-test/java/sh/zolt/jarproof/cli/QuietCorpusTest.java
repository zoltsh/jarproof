package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixtures that exist to prove a check stays quiet.
 *
 * <p>Both members are correct code that a verifier taking a shortcut would report anyway. Nest-based
 * access compiles to direct references to private members on Java 11 and later, so a verifier
 * applying pre-11 access rules manufactures inaccessible-member findings. A signature-polymorphic
 * call compiles against a descriptor no declared method matches, so a verifier resolving descriptors
 * literally manufactures a missing method.
 *
 * <p>Neither member declares a main class, so there is nothing to launch: the fixtures prove their
 * behaviour by compiling and being read, and the assertion is that the report is empty.
 */
final class QuietCorpusTest {
    @TempDir
    Path workspace;

    @Test
    void findsNothingWrongWithNestBasedAccess() {
        staysSilent("nestmates");
    }

    @Test
    void findsNothingWrongWithASignaturePolymorphicCall() {
        staysSilent("method-handle-poly");
    }

    private void staysSilent(String member) {
        Path artifact = FixtureCorpus.jar(member);

        CorpusCheck check = CorpusCheck.reporting(
                workspace.resolve(member + ".json"),
                List.of(CorpusCommand.APPLICATION, artifact.toString()));

        assertEquals(List.of(), check.findings(), check.report());
        assertEquals(0, check.exitCode(), check.err());
    }
}
