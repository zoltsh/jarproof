package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OutputTargetTest {
    @Test
    void writesToTheStreamWhenNoFileWasNamed() throws IOException {
        StringWriter captured = new StringWriter();

        OutputTarget.of(Optional.empty(), new PrintWriter(captured)).write("one\ntwo\n");

        assertEquals("one\ntwo\n", captured.toString());
    }

    @Test
    void writesTheFileAsUtf8BytesAndLeavesTheStreamAlone(@TempDir Path directory) throws IOException {
        Path report = directory.resolve("report.json");
        String document = "{\"ship\": \"" + Character.toString(0x1F680) + "\"}\n";
        StringWriter captured = new StringWriter();

        OutputTarget.of(Optional.of(report), new PrintWriter(captured)).write(document);

        assertArrayEquals(document.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(report));
        assertEquals("", captured.toString());
    }

    @Test
    void keepsLineFeedsExactlyAsRendered(@TempDir Path directory) throws IOException {
        Path report = directory.resolve("report.txt");

        OutputTarget.of(Optional.of(report), quietWriter()).write("a\nb\n");

        assertArrayEquals("a\nb\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(report));
    }

    @Test
    void replacesAnExistingFileRatherThanAppending(@TempDir Path directory) throws IOException {
        Path report = directory.resolve("report.txt");
        OutputTarget target = OutputTarget.of(Optional.of(report), quietWriter());

        target.write("first\n");
        target.write("second\n");

        assertEquals("second\n", Files.readString(report, StandardCharsets.UTF_8));
    }

    @Test
    void reportsAFileItCannotWrite(@TempDir Path directory) {
        Path missing = directory.resolve("absent").resolve("report.txt");
        OutputTarget target = OutputTarget.of(Optional.of(missing), quietWriter());

        assertThrows(IOException.class, () -> target.write("x\n"));
    }

    private static PrintWriter quietWriter() {
        return new PrintWriter(new StringWriter());
    }
}
