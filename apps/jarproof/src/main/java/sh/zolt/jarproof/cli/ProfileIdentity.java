package sh.zolt.jarproof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

/**
 * Names the runtime symbol profile a run measured its findings against.
 *
 * <p>A baseline records this string so a later run can tell whether it is comparing like with like.
 * That makes it a promise about content, not about a machine: the bundled profile is named by the
 * release it describes, and a JDK override is named by the release plus a short digest of the
 * signature archive the symbols came out of. Two checkouts on two machines with the same JDK build
 * therefore agree, while a different JDK build is visibly different even at the same release --
 * which is exactly the question "is this baseline still valid" asks.
 *
 * <p>The digest is content-addressed on purpose. The installation path is deliberately not part of
 * it, because where a JDK happens to be installed says nothing about what it declares.
 */
final class ProfileIdentity {
    private static final String BUNDLED = "bundled:";
    private static final String SUPPLIED = "jdk:";
    private static final String SIGNATURE_DIRECTORY = "lib";
    private static final String SIGNATURE_FILE = "ct.sym";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String HEX_BYTE = "%02x";
    private static final int IDENTITY_BYTES = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final String UNREADABLE = "This JDK has no readable signature archive at ";

    private ProfileIdentity() {
    }

    /**
     * Names the profile a run used.
     *
     * @param javaRelease the verified Java release
     * @param jdkHome the JDK installation that supplied platform symbols, or empty for bundled data
     * @return {@code bundled:<release>}, or {@code jdk:<release>:<digest>} for an override
     * @throws IllegalArgumentException when a named JDK's signature archive cannot be read
     */
    static String of(int javaRelease, Optional<Path> jdkHome) {
        return jdkHome
                .map(home -> SUPPLIED + javaRelease + ':' + digestOf(signatureArchive(home)))
                .orElseGet(() -> BUNDLED + javaRelease);
    }

    /**
     * Locates the signature archive of a JDK installation.
     *
     * @param jdkHome the installation directory
     * @return the path the JDK keeps its class signatures in
     */
    static Path signatureArchive(Path jdkHome) {
        return jdkHome.resolve(SIGNATURE_DIRECTORY).resolve(SIGNATURE_FILE);
    }

    private static String digestOf(Path archive) {
        byte[] digest = digest(archive);
        StringBuilder identity = new StringBuilder();
        for (int index = 0; index < IDENTITY_BYTES; index++) {
            identity.append(String.format(Locale.ROOT, HEX_BYTE, digest[index] & BYTE_MASK));
        }
        return identity.toString();
    }

    private static byte[] digest(Path archive) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(Files.readAllBytes(archive));
        } catch (IOException | NoSuchAlgorithmException unreadable) {
            throw new IllegalArgumentException(UNREADABLE + archive, unreadable);
        }
    }
}
