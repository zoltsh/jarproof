package sh.zolt.jarproof.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Everything one verification run needs: the application artifacts under test, the runtime
 * classpath they will be launched with, the Java runtime they target, and how widely to look.
 *
 * <p>Application order and classpath order are meaningful and preserved exactly as supplied:
 * application roots are searched first, and the first entry that declares a class is the entry
 * that wins at runtime. Both lists are defensively copied and always unmodifiable.
 */
public record VerificationRequest(
        List<Path> applications,
        List<Path> classpath,
        TargetRuntime targetRuntime,
        Scope scope) {
    public VerificationRequest {
        Objects.requireNonNull(applications, "A request needs its application artifacts");
        Objects.requireNonNull(classpath, "A request needs a classpath, even when empty");
        Objects.requireNonNull(targetRuntime, "A request needs a target runtime");
        Objects.requireNonNull(scope, "A request needs an analysis scope");
        if (applications.isEmpty()) {
            throw new IllegalArgumentException("A request needs at least one application artifact");
        }
        applications = List.copyOf(applications);
        classpath = List.copyOf(classpath);
    }
}
