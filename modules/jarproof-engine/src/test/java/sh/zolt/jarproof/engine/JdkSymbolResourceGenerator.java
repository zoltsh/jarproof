package sh.zolt.jarproof.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Rebuilds a committed JDK symbol resource from the signature archive of the JDK the tests
 * themselves run on.
 *
 * <p>This lives in test scope on purpose. Production code reads committed resources and
 * explicitly supplied paths only; locating a JDK through {@code java.home} is a build-time
 * concern, and the build is exactly where the pinned toolchain makes the answer reproducible.
 *
 * <p>The output is deterministic. {@link GZIPOutputStream} writes a fixed ten-byte header with
 * a zero modification time, and every ordering decision inside the text is made by production
 * code, so the same toolchain always produces the same bytes.
 */
final class JdkSymbolResourceGenerator {
    private JdkSymbolResourceGenerator() {
    }

    /** The signature archive shipped by the JDK running these tests. */
    static Path toolchainCtSym() {
        return Path.of(System.getProperty("java.home"), "lib", "ct.sym");
    }

    /** Builds the exact bytes the committed resource for a release should hold. */
    static byte[] resourceBytes(int javaRelease) {
        JdkSymbolCatalog catalog = JdkSymbolCatalog.fromCtSym(toolchainCtSym(), javaRelease);
        StringBuilder text = new StringBuilder();
        text.append(JdkSymbolLines.header(javaRelease, catalog.classCount())).append('\n');
        for (JdkSymbolEntry entry : catalog.entries()) {
            text.append(JdkSymbolLines.encode(entry)).append('\n');
        }
        return compressed(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] compressed(byte[] text) {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(target)) {
            gzip.write(text);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return target.toByteArray();
    }
}
