package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * The layout an application archive uses when it carries its own dependencies, and how to read it.
 *
 * <p>A launcher for such an archive builds a classpath out of two areas inside it: one root holding
 * the application's own classes and one directory holding the library archives it was built against.
 * Both are named here rather than at each use, so the paths jarproof trusts stay in one reviewable
 * place beside {@link ArchiveLayout}.
 *
 * <p><strong>Detection.</strong> The main manifest is authoritative: an archive declaring both
 * {@code Spring-Boot-Classes} and {@code Spring-Boot-Lib} uses exactly the paths it declares. Failing
 * that, the entries decide — the first fallback layout whose classes root prefixes an entry, which is
 * {@code BOOT-INF} for a fat JAR and {@code WEB-INF} for a WAR — and a single declared attribute
 * still overrides its counterpart in that fallback. An archive matching none of this is an ordinary
 * archive and is read as one.
 *
 * <p><strong>Order.</strong> A {@code classpath.idx} beside the library directory names the libraries
 * in the order the launcher adds them, one per line, shaped {@code - "BOOT-INF/lib/name.jar"}. Only
 * that shape is read, because a line jarproof cannot parse is not a line it should guess at.
 */
final class BootLayout {
    /** Separates an archive's own path text from the position inside it a report names. */
    static final String NESTED_SEPARATOR = "!/";

    private static final Attributes.Name DECLARED_CLASSES = new Attributes.Name("Spring-Boot-Classes");
    private static final Attributes.Name DECLARED_LIBRARIES = new Attributes.Name("Spring-Boot-Lib");
    private static final List<BootLayout> FALLBACKS = List.of(
            new BootLayout("BOOT-INF/classes/", "BOOT-INF/lib/"),
            new BootLayout("WEB-INF/classes/", "WEB-INF/lib/"));
    private static final String INDEX_NAME = "classpath.idx";
    private static final String INDEX_LINE_PREFIX = "- ";
    private static final char SEGMENT_END = '/';
    private static final char QUOTE = '"';

    private final String classesRoot;
    private final String libraryDirectory;

    private BootLayout(String classesRoot, String libraryDirectory) {
        this.classesRoot = classesRoot;
        this.libraryDirectory = libraryDirectory;
    }

    /**
     * Decides whether one archive carries its own dependencies, and where.
     *
     * @param manifest the archive's main manifest
     * @param entryNames every entry name the archive declares, directories included
     * @return the layout it uses, or empty when it is an ordinary archive
     */
    static Optional<BootLayout> of(Optional<Manifest> manifest, List<String> entryNames) {
        Optional<String> classes = declared(manifest, DECLARED_CLASSES);
        Optional<String> libraries = declared(manifest, DECLARED_LIBRARIES);
        if (classes.isPresent() && libraries.isPresent()) {
            return Optional.of(new BootLayout(classes.get(), libraries.get()));
        }
        return FALLBACKS.stream()
                .filter(fallback -> entryNames.stream().anyMatch(name -> name.startsWith(fallback.classesRoot)))
                .findFirst()
                .map(fallback -> new BootLayout(
                        classes.orElse(fallback.classesRoot), libraries.orElse(fallback.libraryDirectory)));
    }

    /** Returns the prefix of the area holding the application's own classes. */
    String classesRoot() {
        return classesRoot;
    }

    /** Returns the entry name of the index that orders the nested libraries. */
    String indexPath() {
        int end = libraryDirectory.lastIndexOf(SEGMENT_END, libraryDirectory.length() - 2);
        return libraryDirectory.substring(0, end + 1) + INDEX_NAME;
    }

    /** Returns whether one entry belongs to an area the launcher reads as its own classpath position. */
    boolean holds(String entryName) {
        return entryName.startsWith(classesRoot) || entryName.startsWith(libraryDirectory);
    }

    /**
     * Returns whether one entry is a library archive the launcher would put on the classpath.
     *
     * @param entryName entry name inside the application archive
     * @return whether it is an archive sitting directly in the library directory
     */
    boolean isLibrary(String entryName) {
        if (!entryName.startsWith(libraryDirectory) || !ArchiveLayout.isExpandableArchive(entryName)) {
            return false;
        }
        return entryName.indexOf(SEGMENT_END, libraryDirectory.length()) < 0;
    }

    /**
     * Spells one position inside an archive the way a report shows it.
     *
     * @param outerDisplay the archive's path text exactly as the caller supplied it
     * @param path the entry name or area prefix inside it
     * @return the caller's text, the nesting separator, then the path without a trailing separator
     */
    static String nestedDisplay(String outerDisplay, String path) {
        String named = path.charAt(path.length() - 1) == SEGMENT_END
                ? path.substring(0, path.length() - 1)
                : path;
        return outerDisplay + NESTED_SEPARATOR + named;
    }

    /**
     * Reads the library paths an index names, in the order it names them.
     *
     * @param content the index entry's text
     * @return each named path, with lines of any other shape ignored
     */
    static List<String> indexedPaths(String content) {
        List<String> paths = new ArrayList<>();
        for (String line : content.lines().toList()) {
            String named = unquoted(line.strip());
            if (!named.isEmpty()) {
                paths.add(named);
            }
        }
        return List.copyOf(paths);
    }

    private static String unquoted(String line) {
        if (!line.startsWith(INDEX_LINE_PREFIX)) {
            return "";
        }
        String named = line.substring(INDEX_LINE_PREFIX.length()).strip();
        boolean quoted = named.length() > 1
                && named.charAt(0) == QUOTE
                && named.charAt(named.length() - 1) == QUOTE;
        return quoted ? named.substring(1, named.length() - 1) : named;
    }

    private static Optional<String> declared(Optional<Manifest> manifest, Attributes.Name name) {
        return ArchiveManifest.mainAttribute(manifest, name)
                .filter(declared -> !declared.isBlank())
                .map(BootLayout::normalized);
    }

    private static String normalized(String declared) {
        return declared.charAt(declared.length() - 1) == SEGMENT_END ? declared : declared + SEGMENT_END;
    }
}
