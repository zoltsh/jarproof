package sh.zolt.jarproof.cli;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The library jars this test JVM itself was launched with.
 *
 * <p>This is the healthy corpus, and it is honest for one reason: it was resolved by Zolt from
 * {@code zolt.lock} rather than assembled to make a test pass, so it is a coherent closure of real
 * releases — Guava, Jackson, Netty, and everything they drag in — with the same optional-dependency
 * references and dead paths that make naive verifiers useless on real classpaths.
 *
 * <p>Only archives are collected. The class directories on the same classpath hold jarproof's own
 * Java 21 output, and reporting them against a Java 17 target is a true positive about this build
 * rather than a false positive about a library, so including them would measure the wrong thing.
 *
 * <p>The launcher hands the real classpath to a loader of its own instead of leaving it in
 * {@code java.class.path}, so the loader is asked first and the system property is the fallback for
 * a runner that does launch tests directly.
 */
final class LibraryClosure {
    private static final String CLASS_PATH = "java.class.path";
    private static final String ARCHIVE_SUFFIX = ".jar";

    private LibraryClosure() {
    }

    /** Returns every jar on this JVM's classpath, in classpath order, without repeats. */
    static List<Path> jars() {
        List<Path> jars = new ArrayList<>();
        for (Path entry : entries()) {
            boolean archive = entry.toString().endsWith(ARCHIVE_SUFFIX) && Files.isRegularFile(entry);
            if (archive && !jars.contains(entry)) {
                jars.add(entry);
            }
        }
        return List.copyOf(jars);
    }

    private static List<Path> entries() {
        ClassLoader loader = LibraryClosure.class.getClassLoader();
        if (loader instanceof URLClassLoader urls) {
            return located(urls.getURLs());
        }
        return declared();
    }

    private static List<Path> located(URL[] urls) {
        List<Path> entries = new ArrayList<>();
        for (URL url : urls) {
            entries.add(local(url));
        }
        return entries;
    }

    private static Path local(URL url) {
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException malformed) {
            throw new IllegalStateException(malformed);
        }
    }

    private static List<Path> declared() {
        List<Path> entries = new ArrayList<>();
        for (String entry : System.getProperty(CLASS_PATH).split(File.pathSeparator)) {
            entries.add(Path.of(entry));
        }
        return entries;
    }
}
