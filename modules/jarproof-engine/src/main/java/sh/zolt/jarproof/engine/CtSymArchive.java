package sh.zolt.jarproof.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a JDK {@code lib/ct.sym} signature archive for one Java release.
 *
 * <p>{@code ct.sym} is an ordinary zip. Verified layout, against GraalVM CE 21.0.2,
 * GraalVM CE 21.0.5, Oracle 21.0.5, GraalVM CE 17, and GraalVM 25.0.4:
 *
 * <ul>
 *   <li>Signature entries are named {@code <releases>/<module>/<internal/name>.sig}, where
 *       {@code <releases>} is an unseparated string of one-character release codes. An entry
 *       under {@code 8AL} describes Java 8, 10, and 21 alike, so one entry is shared by every
 *       release whose declarations are identical.
 *   <li>A release code is that release number written as a base-36 digit in upper case:
 *       {@code 8} and {@code 9} for Java 8 and 9, {@code A} for 10, {@code B} for 11,
 *       {@code H} for 17, {@code K} for 20, {@code L} for 21.
 *   <li>Each {@code .sig} file is a real class file — {@code cafebabe}, stamped with the
 *       generating JDK's class file version — carrying declarations with no method bodies.
 *   <li>Each release directory also holds one {@code <module>/module-info.sig} per platform
 *       module. Those describe modules rather than referenceable classes and are skipped.
 *   <li>The single non-signature entry is {@code <release>/system-modules}, a line-per-module
 *       text file naming the platform modules of that release.
 *   <li>A JDK's own release is present as {@code <code>/system-modules} only: a Java 21
 *       {@code ct.sym} carries signatures for Java 8 through 20 and none for 21, because
 *       {@code javac} compiles against the live image for its own release. Signatures for a
 *       release therefore first appear in the {@code ct.sym} of a later JDK.
 * </ul>
 *
 * <p>Reading is deterministic: applicable entries are sorted by name before parsing, so the
 * resulting catalog never depends on zip iteration order.
 */
final class CtSymArchive {
    private static final String SIGNATURE_SUFFIX = ".sig";
    private static final String MODULE_SIGNATURE_SUFFIX = "/module-info.sig";
    private static final String UNREADABLE = "Could not read the ct.sym at ";
    private static final int LOWEST_CODED_RELEASE = 8;
    private static final int HIGHEST_CODED_RELEASE = 35;
    private static final int RELEASE_RADIX = 36;

    private final Path ctSym;
    private final String display;

    /**
     * Binds one signature archive to read.
     *
     * @param ctSym read handle of the archive, already resolved against the engine's base
     * @param display the caller's own text for that archive, which is what a failure names, so no
     *     path this machine resolved ever reaches a report
     */
    CtSymArchive(Path ctSym, String display) {
        this.ctSym = Objects.requireNonNull(ctSym);
        this.display = Objects.requireNonNull(display);
    }

    /**
     * Collects every class the archive declares for one Java release.
     *
     * @throws IllegalArgumentException when the release has no coded form, or when the archive
     *     carries no signatures for it
     * @throws UncheckedIOException when the path is not a readable zip
     */
    JdkSymbolCatalog catalog(int javaRelease) {
        char code = releaseCode(javaRelease);
        try (ZipFile archive = new ZipFile(ctSym.toFile())) {
            List<ZipEntry> signatures = signatureEntries(archive, code);
            if (signatures.isEmpty()) {
                throw new IllegalArgumentException("The ct.sym at " + display
                        + " declares no signatures for Java " + javaRelease
                        + "; it covers releases " + releaseNumbers(archive));
            }
            List<JdkSymbolEntry> entries = new ArrayList<>(signatures.size());
            for (ZipEntry signature : signatures) {
                entries.add(entryOf(archive, signature));
            }
            return JdkSymbolCatalog.of(javaRelease, entries);
        } catch (IOException failure) {
            throw new UncheckedIOException(UNREADABLE + display, failure);
        }
    }

    /** Returns every Java release the archive carries signatures for, ascending. */
    SortedSet<Integer> releases() {
        try (ZipFile archive = new ZipFile(ctSym.toFile())) {
            return releaseNumbers(archive);
        } catch (IOException failure) {
            throw new UncheckedIOException(UNREADABLE + display, failure);
        }
    }

    private static List<ZipEntry> signatureEntries(ZipFile archive, char code) {
        List<ZipEntry> found = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (describesClass(entry.getName(), code)) {
                found.add(entry);
            }
        }
        found.sort(Comparator.comparing(ZipEntry::getName));
        return found;
    }

    private static boolean describesClass(String name, char code) {
        if (!name.endsWith(SIGNATURE_SUFFIX) || name.endsWith(MODULE_SIGNATURE_SUFFIX)) {
            return false;
        }
        int separator = name.indexOf('/');
        return separator > 0 && name.lastIndexOf(code, separator - 1) >= 0;
    }

    private static JdkSymbolEntry entryOf(ZipFile archive, ZipEntry entry) throws IOException {
        String name = entry.getName();
        int releaseEnd = name.indexOf('/');
        int moduleEnd = name.indexOf('/', releaseEnd + 1);
        if (moduleEnd < 0) {
            throw new IllegalStateException("A ct.sym signature entry needs a module segment: " + name);
        }
        try (InputStream bytes = archive.getInputStream(entry)) {
            return new JdkSymbolEntry(
                    name.substring(releaseEnd + 1, moduleEnd),
                    JdkSymbolClassScanner.shapeOf(bytes.readAllBytes()));
        }
    }

    private static SortedSet<Integer> releaseNumbers(ZipFile archive) {
        SortedSet<Integer> releases = new TreeSet<>();
        Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            int separator = name.indexOf('/');
            if (!name.endsWith(SIGNATURE_SUFFIX) || separator <= 0) {
                continue;
            }
            for (int index = 0; index < separator; index++) {
                releases.add(Character.digit(name.charAt(index), RELEASE_RADIX));
            }
        }
        return releases;
    }

    private static char releaseCode(int javaRelease) {
        if (javaRelease < LOWEST_CODED_RELEASE || javaRelease > HIGHEST_CODED_RELEASE) {
            throw new IllegalArgumentException("A ct.sym release code exists only for Java "
                    + LOWEST_CODED_RELEASE + " through " + HIGHEST_CODED_RELEASE + ": " + javaRelease);
        }
        return Character.toUpperCase(Character.forDigit(javaRelease, RELEASE_RADIX));
    }
}
