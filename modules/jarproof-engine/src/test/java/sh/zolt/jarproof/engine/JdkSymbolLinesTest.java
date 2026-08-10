package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class JdkSymbolLinesTest {
    @Test
    void headerCarriesVersionReleaseAndCount() {
        String header = JdkSymbolLines.header(17, 4692);

        assertEquals("#jarproof-jdk-symbols\t1\t17\t4692", header);
        assertEquals(17, JdkSymbolLines.releaseOf(header));
        assertEquals(4692, JdkSymbolLines.classCountOf(header));
    }

    @Test
    void aFullyPopulatedClassRoundTrips() {
        JdkSymbolEntry entry = new JdkSymbolEntry("java.base", new ClassShape(
                "com/example/Outer$Inner",
                0x0021,
                Optional.of("com/example/Parent"),
                List.of("java/io/Serializable", "java/lang/Comparable"),
                Optional.of("com/example/Outer"),
                List.of("com/example/Outer$Other"),
                List.of(
                        new MemberShape("<init>", "()V", 0x0001),
                        new MemberShape("compareTo", "(Ljava/lang/Object;)I", 0x1041),
                        new MemberShape("value", "Ljava/lang/String;", 0x0012))));

        String line = JdkSymbolLines.encode(entry);

        assertEquals(entry, JdkSymbolLines.decode(line));
        assertTrue(line.startsWith("com/example/Outer$Inner\tjava.base\t21\t"), line);
    }

    @Test
    void anEmptyMinimalClassRoundTrips() {
        JdkSymbolEntry entry = new JdkSymbolEntry("java.base", new ClassShape(
                "java/lang/Object",
                0x0021,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                List.of()));

        String line = JdkSymbolLines.encode(entry);

        assertEquals("java/lang/Object\tjava.base\t21\t\t\t\t", line);
        assertEquals(entry, JdkSymbolLines.decode(line));
    }

    @Test
    void accessFlagsUseHexadecimalWithoutPadding() {
        JdkSymbolEntry entry = new JdkSymbolEntry("m", new ClassShape(
                "C", 0x0601, Optional.empty(), List.of(), Optional.empty(), List.of(),
                List.of(new MemberShape("f", "I", 0))));

        assertEquals("C\tm\t601\t\t\t\t\tf\tI\t0", JdkSymbolLines.encode(entry));
        assertEquals(entry, JdkSymbolLines.decode(JdkSymbolLines.encode(entry)));
    }

    @Test
    void aLineWithTooFewFieldsIsRejected() {
        assertThrows(IllegalStateException.class, () -> JdkSymbolLines.decode("C\tm\t21\t\t\t"));
    }

    @Test
    void aLineWithAPartialMemberTripleIsRejected() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> JdkSymbolLines.decode("C\tm\t21\t\t\t\t\tname\tI"));

        assertTrue(failure.getMessage().contains("member triples"), failure::getMessage);
    }

    @Test
    void unparsableAccessFlagsAreRejected() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> JdkSymbolLines.decode("C\tm\tzz\t\t\t\t"));

        assertTrue(failure.getMessage().contains("zz"), failure::getMessage);
    }

    @Test
    void aHeaderWithoutTheMagicIsRejected() {
        assertThrows(IllegalStateException.class, () -> JdkSymbolLines.releaseOf("something else\t1\t17\t0"));
        assertThrows(IllegalStateException.class, () -> JdkSymbolLines.releaseOf("#jarproof-jdk-symbols\t1\t17"));
    }

    @Test
    void anUnknownFormatVersionIsRejected() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> JdkSymbolLines.classCountOf("#jarproof-jdk-symbols\t2\t17\t0"));

        assertTrue(failure.getMessage().contains("version 2"), failure::getMessage);
    }

    @Test
    void entriesRejectMissingParts() {
        ClassShape shape = new ClassShape(
                "C", 0, Optional.empty(), List.of(), Optional.empty(), List.of(), List.of());

        assertThrows(NullPointerException.class, () -> new JdkSymbolEntry(null, shape));
        assertThrows(NullPointerException.class, () -> new JdkSymbolEntry("m", null));
    }
}
