package sh.zolt.jarproof.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the archives a well-behaved packager cannot produce.
 *
 * <p>Every other fixture here writes archives through {@link ZipOutputStream}, which is exactly why it
 * cannot write these: it refuses a duplicate entry name, it refuses a stored entry whose declared CRC
 * disagrees with the bytes it just wrote, and it always finishes the central directory it started. An
 * archive that arrives from a stranger carries no such guarantees, so these are assembled as bytes and
 * patched afterwards, and the patching insists on the number of occurrences it changed rather than
 * hoping the pattern was where it was expected.
 *
 * <p>Nothing here is malformed for its own sake. Each shape corresponds to something real: bytes
 * appended by a self-extracting wrapper, an upload that stopped early, a repacker that recomputed no
 * checksum, a name written by a tool that never heard of a path segment.
 */
final class AdversarialArchives {
    private static final int CRC_BYTES = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final int BYTE_BITS = 8;
    private static final int LOCAL_HEADER_BYTES = 30;
    private static final int LOCAL_NAME_LENGTH_OFFSET = 26;
    private static final int LOCAL_EXTRA_LENGTH_OFFSET = 28;
    private static final int FLIPPED_BITS = 0x5A;

    private AdversarialArchives() {
    }

    /**
     * Writes crafted bytes to a file, so the engine reads exactly what was crafted.
     *
     * @param directory directory to write into
     * @param name file name, which may name directories of its own
     * @param content the archive bytes
     * @return the written file
     */
    static Path write(Path directory, String name, byte[] content) {
        Path archive = directory.resolve(name);
        try {
            Files.createDirectories(archive.getParent());
            try (OutputStream file = Files.newOutputStream(archive)) {
                file.write(content);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return archive;
    }

    /**
     * The bytes of an archive whose named entries are stored rather than deflated.
     *
     * @param entries entry names and content, written in iteration order
     * @param stored names of the entries to store, which a launcher can read in place
     * @return the archive bytes
     */
    static byte[] archive(Map<String, byte[]> entries, Set<String> stored) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(stored.contains(entry.getKey())
                        ? storedEntry(entry.getKey(), entry.getValue())
                        : new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    /**
     * The same archive with bytes appended after its central directory, the way a self-extracting
     * wrapper or a careless concatenation leaves them.
     *
     * @param archive a complete archive
     * @param garbage text to append
     * @return the padded archive
     */
    static byte[] padded(byte[] archive, String garbage) {
        byte[] trailing = garbage.getBytes(StandardCharsets.UTF_8);
        byte[] padded = Arrays.copyOf(archive, archive.length + trailing.length);
        System.arraycopy(trailing, 0, padded, archive.length, trailing.length);
        return padded;
    }

    /**
     * The opening bytes of an archive, as a copy that stopped before the writer finished.
     *
     * @param archive a complete archive
     * @param bytes how many opening bytes survived
     * @return the truncated archive
     */
    static byte[] truncated(byte[] archive, int bytes) {
        return Arrays.copyOf(archive, bytes);
    }

    /**
     * Replaces one byte sequence wherever it occurs, insisting on how many places that was.
     *
     * <p>An entry name lives in both the local header and the central directory, and a CRC-32 lives in
     * both as well, so a patch that changed one of them would leave an archive no reader agrees about.
     * Demanding the count turns a pattern that moved, or that also occurred inside compressed content,
     * into a failed fixture instead of a test that quietly proved something else.
     *
     * @param archive the archive bytes to patch
     * @param find the sequence to replace
     * @param replacement the sequence to write instead, of the same length
     * @param occurrences how many occurrences the caller expects
     * @return the patched archive
     */
    static byte[] replaced(byte[] archive, byte[] find, byte[] replacement, int occurrences) {
        if (find.length != replacement.length) {
            throw new IllegalStateException("A patch may not change the length of an archive");
        }
        byte[] patched = archive.clone();
        int replaced = 0;
        for (int at = 0; at + find.length <= patched.length; at++) {
            if (Arrays.equals(patched, at, at + find.length, find, 0, find.length)) {
                System.arraycopy(replacement, 0, patched, at, replacement.length);
                replaced++;
            }
        }
        if (replaced != occurrences) {
            throw new IllegalStateException("Patched " + replaced + " places, expected " + occurrences);
        }
        return patched;
    }

    /**
     * Flips bits inside the payload of an archive's first entry, leaving every header and the central
     * directory exactly as written.
     *
     * <p>The payload start is read out of the local header rather than assumed, so the damage lands in
     * content whatever the writer put in front of it. A reader that trusts a directory it can still
     * parse therefore hands the engine bytes that are no longer what was compressed -- which is the
     * whole point, since neither {@code ZipFile} nor the engine verifies a checksum on the way in.
     *
     * @param archive a complete archive
     * @param offsetIntoPayload how far into the first entry's payload to strike
     * @return the corrupted archive
     */
    static byte[] corruptedPayload(byte[] archive, int offsetIntoPayload) {
        int nameLength = unsigned(archive, LOCAL_NAME_LENGTH_OFFSET);
        int extraLength = unsigned(archive, LOCAL_EXTRA_LENGTH_OFFSET);
        int at = LOCAL_HEADER_BYTES + nameLength + extraLength + offsetIntoPayload;
        byte[] corrupted = archive.clone();
        corrupted[at] = (byte) (corrupted[at] ^ FLIPPED_BITS);
        return corrupted;
    }

    /**
     * The CRC-32 of some content as an archive header records it: four bytes, least significant first.
     *
     * @param content the entry content
     * @return the recorded checksum bytes
     */
    static byte[] recordedCrc(byte[] content) {
        CRC32 digest = new CRC32();
        digest.update(content);
        long value = digest.getValue();
        byte[] recorded = new byte[CRC_BYTES];
        for (int index = 0; index < CRC_BYTES; index++) {
            recorded[index] = (byte) (value >>> index * BYTE_BITS & BYTE_MASK);
        }
        return recorded;
    }

    /** Reads one of the archive format's two-byte little-endian header fields. */
    private static int unsigned(byte[] archive, int offset) {
        return archive[offset] & BYTE_MASK | (archive[offset + 1] & BYTE_MASK) << BYTE_BITS;
    }

    /** A stored entry needs its size and CRC decided before any of its bytes are written. */
    private static ZipEntry storedEntry(String entryName, byte[] content) {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        CRC32 digest = new CRC32();
        digest.update(content);
        entry.setCrc(digest.getValue());
        return entry;
    }
}
