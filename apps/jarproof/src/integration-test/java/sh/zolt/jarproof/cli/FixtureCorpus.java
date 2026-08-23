package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The packaged fixture corpus, addressed as test data.
 *
 * <p>A fixture member is never a dependency of this harness — the whole point of the v1/v2 pairs is
 * that the dependency is wrong — so the corpus is reached the way a user reaches a release artifact:
 * by path, after {@code zolt package --workspace --all} has written it. A missing artifact therefore
 * is not a test failure to puzzle over but a build step nobody ran, and it is reported as exactly
 * that.
 *
 * <p>The workspace root is found by walking up to the manifest that declares this workspace, and
 * nothing else is assumed about where the tests are running: {@code zolt integration-test} points
 * {@code user.dir} at the member under test while leaving the process working directory at the root,
 * so the two disagree and only the manifest settles the question. Artifacts are therefore handed to
 * the tool as absolute paths unless a test is specifically about relative path text.
 */
final class FixtureCorpus {
    private static final String MANIFEST = "zolt.toml";
    private static final String WORKSPACE_DECLARATION = "[workspace.members]";
    private static final String FIXTURES = "fixtures";
    private static final String APPLICATION_DIRECTORY = "apps/jarproof";
    private static final String TARGET = "target";
    private static final String ARTIFACT_PREFIX = "jarproof-fixture-";
    private static final String COMMAND_LINE_PREFIX = "jarproof-cli";
    private static final String ARTIFACT_SUFFIX = "-0.0.1-SNAPSHOT.jar";
    private static final String NOT_PACKAGED = "Packaged artifact is missing: ";
    private static final String PACKAGE_FIRST = "\nRun `zolt package --workspace --all` before"
            + " `zolt integration-test`; corpus artifacts are test data, not dependencies.";

    private FixtureCorpus() {
    }

    /** Returns the workspace root, found by walking up from the working directory. */
    static Path workspaceRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            Path manifest = candidate.resolve(MANIFEST);
            if (Files.isRegularFile(manifest) && text(manifest).contains(WORKSPACE_DECLARATION)) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the jarproof workspace root");
    }

    /**
     * Returns the packaged artifact of one fixture member.
     *
     * @param member the member directory name under {@code fixtures/}
     * @return the absolute path of its packaged jar
     * @throws IllegalStateException when the artifact has not been packaged
     */
    static Path jar(String member) {
        Path artifact = workspaceRoot()
                .resolve(FIXTURES)
                .resolve(member)
                .resolve(TARGET)
                .resolve(ARTIFACT_PREFIX + member + ARTIFACT_SUFFIX);
        if (!Files.isRegularFile(artifact)) {
            throw new IllegalStateException(NOT_PACKAGED + artifact + PACKAGE_FIRST);
        }
        return artifact;
    }

    /**
     * Returns the packaged command-line archive, which is run as a real process where the working
     * directory matters.
     *
     * @return the absolute path of the uber jar
     * @throws IllegalStateException when it has not been packaged
     */
    static Path commandLineJar() {
        Path artifact = workspaceRoot()
                .resolve(APPLICATION_DIRECTORY)
                .resolve(TARGET)
                .resolve(COMMAND_LINE_PREFIX + ARTIFACT_SUFFIX);
        if (!Files.isRegularFile(artifact)) {
            throw new IllegalStateException(NOT_PACKAGED + artifact + PACKAGE_FIRST);
        }
        return artifact;
    }

    /**
     * Returns one artifact as the workspace-relative text a developer would write for it.
     *
     * <p>This is both what a machine report prints when it is rooted at the workspace and what a
     * baseline should be recorded from. A fingerprint keeps the caller's path text verbatim, so a
     * baseline recorded from an absolute path is valid on exactly one machine; relative text keeps
     * the recorded file byte-identical everywhere, which is what a committed baseline needs.
     *
     * @param artifact an absolute path inside the workspace
     * @return the same artifact, relative to the workspace root, with forward slashes
     */
    static String relativePath(Path artifact) {
        return workspaceRoot().relativize(artifact).toString().replace('\\', '/');
    }

    /**
     * Copies artifacts into one directory so a single wildcard entry can expand to all of them.
     *
     * @param directory the directory to stage into
     * @param artifacts the artifacts to copy, keeping their file names
     * @return the wildcard classpath entry naming that directory
     */
    static String stage(Path directory, List<Path> artifacts) {
        try {
            Files.createDirectories(directory);
            for (Path artifact : artifacts) {
                Files.copy(
                        artifact,
                        directory.resolve(artifact.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return directory.resolve("*").toString();
    }

    private static String text(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
