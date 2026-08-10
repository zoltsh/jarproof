package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Option;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;

/**
 * The flags that describe what to verify, shared by every command that verifies something.
 *
 * <p>{@code check} and {@code baseline} answer the same question about the same inputs and differ
 * only in what they do with the answer, so they declare these flags once here. That is not merely
 * less typing: two copies of {@code --target-java} could drift in spelling, in default, or in
 * meaning, and a baseline recorded under different flags than the check that reads it is worse than
 * no baseline.
 *
 * <p>Paths reach the engine exactly as the caller wrote them. Nothing is absolutized and nothing is
 * normalised, because the report repeats that text and two machines have to produce the same bytes
 * from the same command line.
 */
final class RequestOptions {
    @Option(
            names = "--application",
            required = true,
            description = "Application artifact under test: a JAR or a class directory."
                    + " Repeatable, and the order is preserved.")
    private List<Path> applications;

    @Option(
            names = "--classpath",
            description = "Runtime classpath entry: a JAR, a class directory, a dir/* wildcard, or"
                    + " @file holding one entry per line. Repeatable, and the order decides which copy wins.")
    private List<String> classpath = List.of();

    @Option(names = "--target-java", required = true, description = "Java release the classpath will run on.")
    private int targetJava;

    @Option(
            names = "--enable-preview",
            description = "Accept preview classfiles, for exactly the target release.")
    private boolean enablePreview;

    @Option(
            names = "--scope",
            description = "Which bytecode a linkage finding may come from: application|all|reachable."
                    + " Defaults to application. reachable is the strictest and quietest: it reports only"
                    + " the findings a method-level call graph proves executable from first-party code,"
                    + " every one of them an error whatever its origin.")
    private Scope scope = Scope.APPLICATION;

    @Option(
            names = "--jdk",
            description = "Read platform symbols from this JDK installation instead of the bundled data.")
    private Path jdk;

    @Option(
            names = "--path-root",
            description = "Root that machine output measures artifact paths from."
                    + " Defaults to the working directory.")
    private Path pathRoot = Path.of("");

    /**
     * Builds the request these flags describe.
     *
     * @return the verification request, with classpath lists already expanded
     * @throws IllegalArgumentException when a flag value cannot describe a request
     * @throws IOException when a named classpath list cannot be read
     */
    VerificationRequest request() throws IOException {
        return new VerificationRequest(
                applications, ClasspathEntries.of(classpath), runtime(), scope, jdkHome());
    }

    /** Returns the JDK installation that supplies platform symbols, or empty for bundled data. */
    Optional<Path> jdkHome() {
        return Optional.ofNullable(jdk);
    }

    /** Returns the identity of the runtime symbol profile this run measures against. */
    String profile() {
        return ProfileIdentity.of(targetJava, jdkHome());
    }

    /** Returns the root machine output measures artifact paths from. */
    PathRoot pathRoot() {
        return PathRoot.of(pathRoot);
    }

    private TargetRuntime runtime() {
        return new TargetRuntime(targetJava, enablePreview ? PreviewMode.ENABLED : PreviewMode.DISABLED);
    }
}
