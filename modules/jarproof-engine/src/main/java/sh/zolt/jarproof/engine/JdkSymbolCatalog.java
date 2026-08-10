package sh.zolt.jarproof.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * The platform API surface of one Java release: which classes exist, what they declare, and
 * which platform module owns each of them.
 *
 * <p>A catalog is built once and answered from memory afterwards. Nothing is read lazily and
 * nothing is cached statically, so a caller decides how long a catalog lives. Two sources
 * exist. {@link #forRelease(int)} loads a resource committed beside this class, which is what
 * lets a native binary answer for a release with no JDK anywhere on the machine.
 * {@link #fromCtSym(Path, int)} reads a JDK's own {@code lib/ct.sym} instead, which is how an
 * explicit JDK override serves releases that are not bundled.
 *
 * <p>Bundled releases are exactly those the repository's pinned toolchain can regenerate
 * byte-for-byte: a {@code ct.sym} never carries signatures for its own JDK's release, so the
 * bundled set trails the toolchain by one release.
 *
 * <h2>Resource format</h2>
 *
 * <p>One file per release sits beside this class, named {@code jdk-symbols-<release>.bin}: a
 * GZIP stream wrapping UTF-8 text. Every text line ends with one newline, the last line
 * included. Line one is the header. Each later line describes exactly one class, and those
 * lines ascend by class internal name — which is also plain ascending order of the line text,
 * because the tab separator sorts below every character a class name can hold.
 *
 * <pre>
 * header    = "#jarproof-jdk-symbols" TAB formatVersion TAB javaRelease TAB classCount
 * classLine = internalName TAB module TAB access TAB superName TAB interfaces TAB nestHost
 *             TAB nestMembers *( TAB memberName TAB memberDescriptor TAB memberAccess )
 * </pre>
 *
 * <ul>
 *   <li>{@code formatVersion} is a decimal integer; a reader refuses a version it does not know.
 *   <li>{@code javaRelease} is the decimal Java feature release, and {@code classCount} is the
 *       decimal number of class lines that follow. A reader checks both.
 *   <li>{@code internalName} is the JVM internal form, such as {@code java/util/Map$Entry}.
 *   <li>{@code module} is the owning platform module, such as {@code java.base}.
 *   <li>{@code access} fields hold the class file {@code access_flags} as lower-case
 *       hexadecimal with no radix prefix, exactly as declared, with nothing synthesized.
 *   <li>{@code superName} is the superclass internal name, and is empty only for
 *       {@code java/lang/Object}.
 *   <li>{@code interfaces} is a comma-separated list in declared order, because that order
 *       decides which inherited default method is maximally specific. It is empty when the
 *       class declares none.
 *   <li>{@code nestHost} is the {@code NestHost} target, empty when the attribute is absent;
 *       {@code nestMembers} is the {@code NestMembers} list in ascending order, empty when the
 *       attribute is absent.
 *   <li>Member triples repeat to the end of the line, fields and methods together, ascending
 *       by name then descriptor. A descriptor beginning with {@code (} marks a method.
 *   <li>Nothing linkage checking cannot use is stored: no annotations, no generic signatures,
 *       no source or line tables, no documentation.
 * </ul>
 *
 * <p>The grammar assumes no field holds a tab and no name inside a list field holds a comma.
 * Platform symbol data written by {@code javac} satisfies both; anything else is malformed
 * data, and a reader reports it as such rather than guessing.
 */
final class JdkSymbolCatalog {
    private static final Set<Integer> BUNDLED_RELEASES = Set.of(17);
    private static final String RESOURCE_PREFIX = "jdk-symbols-";
    private static final String RESOURCE_SUFFIX = ".bin";

    private final int javaRelease;
    private final Map<String, JdkSymbolEntry> entriesByName;

    private JdkSymbolCatalog(int javaRelease, Map<String, JdkSymbolEntry> entriesByName) {
        this.javaRelease = javaRelease;
        this.entriesByName = entriesByName;
    }

    /**
     * Loads the catalog committed for a bundled release.
     *
     * @throws IllegalArgumentException when no resource is bundled for the release
     */
    static JdkSymbolCatalog forRelease(int javaRelease) {
        if (!BUNDLED_RELEASES.contains(javaRelease)) {
            throw new IllegalArgumentException("Java " + javaRelease
                    + " has no bundled JDK symbols; bundled releases are " + BUNDLED_RELEASES);
        }
        return fromResource(resourceName(javaRelease), javaRelease);
    }

    /** Builds a catalog for any release a JDK's own signature archive declares. */
    static JdkSymbolCatalog fromCtSym(Path ctSym, int javaRelease) {
        return new CtSymArchive(ctSym).catalog(javaRelease);
    }

    /** Indexes entries by internal name, keeping ascending name order for canonical output. */
    static JdkSymbolCatalog of(int javaRelease, Collection<JdkSymbolEntry> entries) {
        Map<String, JdkSymbolEntry> ascending = new TreeMap<>();
        for (JdkSymbolEntry entry : entries) {
            ascending.put(entry.shape().internalName(), entry);
        }
        return new JdkSymbolCatalog(javaRelease, Collections.unmodifiableMap(new LinkedHashMap<>(ascending)));
    }

    /** Reads a catalog from a GZIP stream of resource text, closing the stream. */
    static JdkSymbolCatalog read(InputStream compressed, int expectedRelease) {
        try (InputStream source = compressed;
                BufferedReader lines = new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(source), StandardCharsets.UTF_8))) {
            String header = lines.readLine();
            if (header == null) {
                throw new IllegalStateException("A JDK symbol resource cannot be empty");
            }
            int release = JdkSymbolLines.releaseOf(header);
            if (release != expectedRelease) {
                throw new IllegalStateException("A JDK symbol resource for Java " + expectedRelease
                        + " declares Java " + release);
            }
            List<JdkSymbolEntry> entries = new ArrayList<>();
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                entries.add(JdkSymbolLines.decode(line));
            }
            return counted(release, entries, JdkSymbolLines.classCountOf(header));
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "Could not read the JDK symbol resource for Java " + expectedRelease, failure);
        }
    }

    private static JdkSymbolCatalog counted(int release, List<JdkSymbolEntry> entries, int declared) {
        if (entries.size() != declared) {
            throw new IllegalStateException("A JDK symbol resource declares " + declared
                    + " classes but carries " + entries.size());
        }
        return of(release, entries);
    }

    /** Reads a catalog from a resource committed beside this class. */
    static JdkSymbolCatalog fromResource(String resource, int expectedRelease) {
        InputStream compressed = JdkSymbolCatalog.class.getResourceAsStream(resource);
        if (compressed == null) {
            throw new IllegalStateException("The build did not bundle the resource " + resource);
        }
        return read(compressed, expectedRelease);
    }

    /** Returns the resource file name a bundled release is committed under. */
    static String resourceName(int javaRelease) {
        return RESOURCE_PREFIX + javaRelease + RESOURCE_SUFFIX;
    }

    /** The Java feature release this catalog describes. */
    int javaRelease() {
        return javaRelease;
    }

    /** How many classes the catalog knows. */
    int classCount() {
        return entriesByName.size();
    }

    /** Every entry, ascending by class internal name. */
    List<JdkSymbolEntry> entries() {
        return List.copyOf(entriesByName.values());
    }

    /** The declared shape of a platform class, by internal name. */
    Optional<ClassShape> classShape(String internalName) {
        return entry(internalName).map(JdkSymbolEntry::shape);
    }

    /** The platform module that owns a class, by internal name. */
    Optional<String> owningModule(String internalName) {
        return entry(internalName).map(JdkSymbolEntry::module);
    }

    private Optional<JdkSymbolEntry> entry(String internalName) {
        return Optional.ofNullable(entriesByName.get(internalName));
    }
}
