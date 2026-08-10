package sh.zolt.jarproof.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one verification run needs: the application artifacts under test, the runtime
 * classpath they will be launched with, the Java runtime they target, how widely to look, and
 * where the platform symbols for that runtime come from.
 *
 * <p>Application order and classpath order are meaningful and preserved exactly as supplied:
 * application roots are searched first, and the first entry that declares a class is the entry
 * that wins at runtime. Both lists are defensively copied and always unmodifiable.
 *
 * <p>{@code jdkHome} names the JDK installation whose signature archive supplies platform symbols
 * instead of the bundled data. It is empty for the common case, where the bundled profile for the
 * target release answers and no JDK has to be installed at all. It never changes which release is
 * verified: the target release comes from {@code targetRuntime} and nothing else.
 */
public record VerificationRequest(
        List<Path> applications,
        List<Path> classpath,
        TargetRuntime targetRuntime,
        Scope scope,
        Optional<Path> jdkHome) {
    public VerificationRequest {
        Objects.requireNonNull(applications, "A request needs its application artifacts");
        Objects.requireNonNull(classpath, "A request needs a classpath, even when empty");
        Objects.requireNonNull(targetRuntime, "A request needs a target runtime");
        Objects.requireNonNull(scope, "A request needs an analysis scope");
        Objects.requireNonNull(jdkHome, "A request needs a platform symbol decision");
        if (applications.isEmpty()) {
            throw new IllegalArgumentException("A request needs at least one application artifact");
        }
        applications = List.copyOf(applications);
        classpath = List.copyOf(classpath);
    }

    /**
     * Creates a request that reads platform symbols from the bundled data for the target release.
     *
     * @param applications application artifacts under test, in the order they are searched
     * @param classpath runtime classpath entries, in the order the runtime searches them
     * @param targetRuntime the Java runtime the classpath will be launched on
     * @param scope which bytecode origins may produce linkage findings
     * @return a request with no JDK installation override
     */
    public static VerificationRequest of(
            List<Path> applications, List<Path> classpath, TargetRuntime targetRuntime, Scope scope) {
        return new VerificationRequest(applications, classpath, targetRuntime, scope, Optional.empty());
    }
}
