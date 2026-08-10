package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

final class JdkSymbolCatalogTest {
    private static final int BUNDLED_RELEASE = 17;
    private static final int PUBLIC = 0x0001;
    private static final int PUBLIC_SUPER = 0x0021;
    private static final int PUBLIC_INTERFACE_ABSTRACT = 0x0601;

    @Test
    void bundledCatalogKnowsItsRelease() {
        JdkSymbolCatalog catalog = JdkSymbolCatalog.forRelease(BUNDLED_RELEASE);

        assertEquals(BUNDLED_RELEASE, catalog.javaRelease());
        assertTrue(catalog.classCount() > 4000, () -> "Only " + catalog.classCount() + " classes");
        assertEquals(catalog.classCount(), catalog.entries().size());
    }

    @Test
    void objectResolvesWithItsDeclaredMembers() {
        ClassShape object = shapeOf("java/lang/Object");

        assertEquals(PUBLIC_SUPER, object.accessFlags());
        assertEquals(Optional.empty(), object.superInternalName());
        assertEquals(List.of(), object.interfaceInternalNames());
        assertTrue(object.members().contains(new MemberShape("equals", "(Ljava/lang/Object;)Z", PUBLIC)));
        assertTrue(object.members().contains(new MemberShape("toString", "()Ljava/lang/String;", PUBLIC)));
        assertTrue(object.members().contains(new MemberShape("<init>", "()V", PUBLIC)));
        assertTrue(object.members().contains(new MemberShape("hashCode", "()I", 0x0101)));
    }

    @Test
    void membersAreOrderedByNameThenDescriptor() {
        List<MemberShape> members = shapeOf("java/lang/Object").members();

        List<String> keys = members.stream().map(member -> member.name() + member.descriptor()).toList();
        assertEquals(keys.stream().sorted().toList(), keys);
    }

    @Test
    void platformModulesAreAttributedToTheirOwner() {
        JdkSymbolCatalog catalog = JdkSymbolCatalog.forRelease(BUNDLED_RELEASE);

        assertEquals(Optional.of("java.sql"), catalog.owningModule("java/sql/Connection"));
        assertEquals(Optional.of("java.sql"), catalog.owningModule("java/sql/DriverManager"));
        assertEquals(Optional.of("java.base"), catalog.owningModule("java/lang/Object"));
        assertEquals(Optional.of("java.xml"), catalog.owningModule("javax/xml/parsers/DocumentBuilder"));
    }

    @Test
    void interfaceHierarchyStaysIntact() {
        ClassShape list = shapeOf("java/util/List");

        assertEquals(PUBLIC_INTERFACE_ABSTRACT, list.accessFlags());
        assertEquals(Optional.of("java/lang/Object"), list.superInternalName());
        assertEquals(List.of("java/util/Collection"), list.interfaceInternalNames());
        assertEquals(List.of("java/lang/Iterable"), shapeOf("java/util/Collection").interfaceInternalNames());
        assertEquals(
                List.of("java/sql/Wrapper", "java/lang/AutoCloseable"),
                shapeOf("java/sql/Connection").interfaceInternalNames());
    }

    @Test
    void nestAttributesSurvive() {
        assertEquals(Optional.of("java/util/Map"), shapeOf("java/util/Map$Entry").nestHostInternalName());
        assertTrue(shapeOf("java/util/Map").nestMemberInternalNames().contains("java/util/Map$Entry"));
    }

    @Test
    void releaseSpecificClassesAreAbsentFromEarlierReleases() {
        JdkSymbolCatalog catalog = JdkSymbolCatalog.forRelease(BUNDLED_RELEASE);

        assertEquals(Optional.empty(), catalog.classShape("java/util/SequencedCollection"));
        assertEquals(Optional.empty(), catalog.owningModule("java/util/SequencedCollection"));
        assertEquals(Optional.empty(), catalog.classShape("com/example/NotAPlatformClass"));
    }

    @Test
    void unsupportedReleasesNameTheBundledSet() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> JdkSymbolCatalog.forRelease(20));

        assertTrue(failure.getMessage().contains("Java 20"), failure::getMessage);
        assertTrue(failure.getMessage().contains(String.valueOf(BUNDLED_RELEASE)), failure::getMessage);
        assertThrows(IllegalArgumentException.class, () -> JdkSymbolCatalog.forRelease(24));
    }

    @Test
    void theBundledResourceAgreesWithTheToolchainArchive() {
        List<JdkSymbolEntry> bundled = JdkSymbolCatalog.forRelease(BUNDLED_RELEASE).entries();
        List<JdkSymbolEntry> parsed = JdkSymbolResourceGenerator.catalogOf(BUNDLED_RELEASE).entries();

        assertEquals(parsed.size(), bundled.size());
        for (int index = 0; index < parsed.size(); index++) {
            assertEquals(JdkSymbolLines.encode(parsed.get(index)), JdkSymbolLines.encode(bundled.get(index)));
        }
    }

    @Test
    void aMissingResourceIsReportedByName() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> JdkSymbolCatalog.fromResource("jdk-symbols-absent.bin", BUNDLED_RELEASE));

        assertTrue(failure.getMessage().contains("jdk-symbols-absent.bin"), failure::getMessage);
    }

    @Test
    void resourceNamesFollowTheReleaseNumber() {
        assertEquals("jdk-symbols-17.bin", JdkSymbolCatalog.resourceName(17));
        assertEquals("jdk-symbols-21.bin", JdkSymbolCatalog.resourceName(21));
    }

    @Test
    void bytesThatAreNotCompressedTextAreRejected() {
        byte[] plain = "not a gzip stream".getBytes(StandardCharsets.UTF_8);

        assertThrows(
                UncheckedIOException.class,
                () -> JdkSymbolCatalog.read(new ByteArrayInputStream(plain), BUNDLED_RELEASE));
    }

    @Test
    void anEmptyResourceIsRejected() {
        assertThrows(IllegalStateException.class, () -> read("", BUNDLED_RELEASE));
    }

    @Test
    void aResourceForAnotherReleaseIsRejected() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> read(JdkSymbolLines.header(21, 0) + "\n", BUNDLED_RELEASE));

        assertTrue(failure.getMessage().contains("declares Java 21"), failure::getMessage);
    }

    @Test
    void aResourceWhoseClassCountDisagreesIsRejected() {
        String text = JdkSymbolLines.header(BUNDLED_RELEASE, 7) + "\n"
                + JdkSymbolLines.encode(entry()) + "\n";

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> read(text, BUNDLED_RELEASE));
        assertTrue(failure.getMessage().contains("declares 7 classes"), failure::getMessage);
    }

    @Test
    void aHandBuiltResourceRoundTrips() {
        String text = JdkSymbolLines.header(BUNDLED_RELEASE, 1) + "\n"
                + JdkSymbolLines.encode(entry()) + "\n";

        JdkSymbolCatalog catalog = read(text, BUNDLED_RELEASE);

        assertEquals(List.of(entry()), catalog.entries());
        assertEquals(Optional.of("example.module"), catalog.owningModule("com/example/Widget"));
        assertFalse(catalog.classShape("com/example/Widget").isEmpty());
    }

    @Test
    void laterEntriesForOneClassWin() {
        JdkSymbolEntry replacement = new JdkSymbolEntry("other.module", entry().shape());

        JdkSymbolCatalog catalog = JdkSymbolCatalog.of(BUNDLED_RELEASE, List.of(entry(), replacement));

        assertEquals(1, catalog.classCount());
        assertEquals(Optional.of("other.module"), catalog.owningModule("com/example/Widget"));
    }

    private static JdkSymbolEntry entry() {
        return new JdkSymbolEntry("example.module", new ClassShape(
                "com/example/Widget",
                PUBLIC_SUPER,
                Optional.of("java/lang/Object"),
                List.of("java/io/Serializable"),
                Optional.empty(),
                List.of(),
                List.of(new MemberShape("value", "I", 0x0002))));
    }

    private static ClassShape shapeOf(String internalName) {
        return JdkSymbolCatalog.forRelease(BUNDLED_RELEASE)
                .classShape(internalName)
                .orElseThrow(() -> new AssertionError("Missing " + internalName));
    }

    private static JdkSymbolCatalog read(String text, int expectedRelease) {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return JdkSymbolCatalog.read(new ByteArrayInputStream(compressed.toByteArray()), expectedRelease);
    }
}
