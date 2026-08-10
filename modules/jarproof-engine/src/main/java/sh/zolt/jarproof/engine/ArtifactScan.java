package sh.zolt.jarproof.engine;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;

/**
 * Accumulates one artifact's classes and the diagnostics that reading them produced.
 *
 * <p>Each entry is checked for the class file signature before anything else, digested so two copies
 * of a class can be compared without keeping both in memory, and then parsed. A class file newer
 * than any parser here understands is recorded with its version and no shape: it is not corrupt, it
 * is simply from the future, and the version check reports that far more usefully than a parse
 * failure would.
 */
final class ArtifactScan {
    private static final int MINIMUM_CLASS_FILE_BYTES = 8;
    private static final int SIGNATURE_HIGH = 0xCAFE;
    private static final int SIGNATURE_LOW = 0xBABE;
    private static final int SIGNATURE_LOW_OFFSET = 2;
    private static final int MINOR_VERSION_OFFSET = 4;
    private static final int MAJOR_VERSION_OFFSET = 6;
    private static final int HIGHEST_PARSED_MAJOR = Opcodes.V26;
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final ClasspathEntry entry;
    private final List<IndexedClass> classes = new ArrayList<>();
    private final List<Finding> findings = new ArrayList<>();

    ArtifactScan(ClasspathEntry entry) {
        this.entry = entry;
    }

    /** Records a diagnostic the surrounding reader produced about this artifact. */
    void addFinding(Finding finding) {
        findings.add(finding);
    }

    /**
     * Records one selected class entry.
     *
     * @param baseEntryName entry name with any multi-release prefix removed, which names the class
     * @param entryName entry the bytes were read from
     * @param classFile the entry's bytes
     */
    void addClassEntry(String baseEntryName, String entryName, byte[] classFile) {
        if (!isClassFile(classFile)) {
            findings.add(CorruptClassFinding.of(entry, entryName));
            return;
        }
        int major = unsignedShort(classFile, MAJOR_VERSION_OFFSET);
        Optional<ClassStructure> structure = structure(entryName, classFile, major);
        classes.add(new IndexedClass(
                entryName,
                ArchiveLayout.internalName(baseEntryName),
                major,
                unsignedShort(classFile, MINOR_VERSION_OFFSET),
                digest(classFile),
                structure.map(ClassStructure::shape),
                structure.map(ClassStructure::references).orElseGet(ClassReferences::empty),
                structure.flatMap(ClassStructure::sourceFile)));
    }

    /** Returns the classes read so far, in the order they were recorded. */
    List<IndexedClass> classes() {
        return List.copyOf(classes);
    }

    /** Returns the diagnostics produced so far. */
    List<Finding> findings() {
        return List.copyOf(findings);
    }

    private Optional<ClassStructure> structure(String entryName, byte[] classFile, int major) {
        if (major > HIGHEST_PARSED_MAJOR) {
            return Optional.empty();
        }
        Optional<ClassStructure> parsed = ClassStructureVisitor.parse(classFile);
        if (parsed.isEmpty()) {
            findings.add(CorruptClassFinding.of(entry, entryName));
        }
        return parsed;
    }

    private static boolean isClassFile(byte[] classFile) {
        return classFile.length >= MINIMUM_CLASS_FILE_BYTES
                && unsignedShort(classFile, 0) == SIGNATURE_HIGH
                && unsignedShort(classFile, SIGNATURE_LOW_OFFSET) == SIGNATURE_LOW;
    }

    private static int unsignedShort(byte[] classFile, int offset) {
        return ((classFile[offset] & 0xFF) << 8) | (classFile[offset + 1] & 0xFF);
    }

    private static String digest(byte[] classFile) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(classFile));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
