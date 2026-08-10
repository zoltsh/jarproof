package sh.zolt.jarproof.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.PreviewMode;
import sh.zolt.jarproof.api.Scope;
import sh.zolt.jarproof.api.TargetRuntime;
import sh.zolt.jarproof.api.VerificationRequest;
import sh.zolt.jarproof.api.VerificationResult;

/** Builds real archives, class directories, and class files for the engine tests to read. */
final class EngineFixture {
    static final String OBJECT = "java/lang/Object";
    static final int JAVA_17_MAJOR = 61;
    static final int JAVA_21_MAJOR = 65;
    static final int PREVIEW_MINOR = 0xFFFF;

    private EngineFixture() {
    }

    static byte[] classFile(String internalName) {
        return classFile(internalName, Opcodes.V17);
    }

    static byte[] classFile(String internalName, int version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, OBJECT, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    static byte[] classFileWithField(String internalName, String fieldName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, OBJECT, null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fieldName, "I", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    static byte[] withVersion(byte[] classFile, int major, int minor) {
        byte[] patched = classFile.clone();
        patched[4] = (byte) (minor >>> 8);
        patched[5] = (byte) minor;
        patched[6] = (byte) (major >>> 8);
        patched[7] = (byte) major;
        return patched;
    }

    static Manifest manifest() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        return manifest;
    }

    static byte[] manifestBytes(Manifest manifest) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            manifest.write(bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    static Map<String, byte[]> entries() {
        return new LinkedHashMap<>();
    }

    static Map<String, byte[]> entries(String name, byte[] content) {
        Map<String, byte[]> entries = entries();
        entries.put(name, content);
        return entries;
    }

    static Map<String, byte[]> withManifest(Map<String, byte[]> entries, Manifest manifest) {
        Map<String, byte[]> combined = entries();
        combined.put(JarFile.MANIFEST_NAME, manifestBytes(manifest));
        combined.putAll(entries);
        return combined;
    }

    static Path jar(Path directory, String name, Map<String, byte[]> entries) {
        Path archive = directory.resolve(name);
        try {
            Files.createDirectories(archive.getParent());
            try (OutputStream file = Files.newOutputStream(archive);
                    ZipOutputStream zip = new ZipOutputStream(file)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return archive;
    }

    static Path classDirectory(Path directory, String name, Map<String, byte[]> entries) {
        Path root = directory.resolve(name);
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                Path file = root.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return root;
    }

    static VerificationRequest request(List<Path> applications, List<Path> classpath, int release) {
        return new VerificationRequest(applications, classpath, TargetRuntime.of(release), Scope.APPLICATION);
    }

    static VerificationRequest previewRequest(List<Path> applications, int release) {
        return new VerificationRequest(
                applications,
                List.of(),
                new TargetRuntime(release, PreviewMode.ENABLED),
                Scope.APPLICATION);
    }

    static List<Finding> verify(VerificationRequest request) {
        VerificationResult result = Jarproof.verify(request);
        return result.findings();
    }

    static List<Finding> verify(List<Path> applications, List<Path> classpath, int release) {
        return verify(request(applications, classpath, release));
    }

    static List<String> codes(List<Finding> findings) {
        return findings.stream().map(finding -> finding.code().value()).toList();
    }

    static Optional<Finding> coded(List<Finding> findings, String code) {
        return findings.stream().filter(finding -> finding.code().value().equals(code)).findFirst();
    }

    static Finding required(List<Finding> findings, String code) {
        return coded(findings, code).orElseThrow(() -> new IllegalStateException(code + " missing from " + findings));
    }

    static List<String> evidence(Finding finding) {
        return finding.evidence().stream().map(sh.zolt.jarproof.api.Evidence::detail).toList();
    }
}
