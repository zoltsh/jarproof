package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.jarproof.api.Finding;

final class ClasspathExpanderTest {
    @TempDir
    Path workspace;

    @Test
    void keepsApplicationRootsFirstInSuppliedOrder() {
        Path web = jar("app-web.jar", "com/acme/web/Web");
        Path core = jar("app-core.jar", "com/acme/core/Core");
        Path library = jar("lib/library.jar", "com/acme/lib/Library");

        List<ClasspathEntry> entries = expand(List.of(web, core), List.of(library)).entries();

        assertEquals(List.of(web.toString(), core.toString(), library.toString()), displays(entries));
        assertEquals(
                List.of(ClasspathOrigin.APPLICATION, ClasspathOrigin.APPLICATION, ClasspathOrigin.CLASSPATH),
                entries.stream().map(ClasspathEntry::origin).toList());
    }

    @Test
    void expandsAWildcardInSortedFileNameOrder() {
        jar("lib/zebra.jar", "com/acme/lib/Zebra");
        jar("lib/apple.jar", "com/acme/lib/Apple");
        jar("lib/ignored.txt", "com/acme/lib/Ignored");
        Path wildcard = workspace.resolve("lib").resolve("*");

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(wildcard)).entries();

        assertEquals(
                List.of(workspace.resolve("lib/apple.jar").toString(), workspace.resolve("lib/zebra.jar").toString()),
                displays(entries).subList(1, entries.size()));
        assertEquals(Optional.of(wildcard.toString()), entries.get(1).wildcardSource());
    }

    @Test
    void treatsAnUpperCaseArchiveSuffixAsExpandable() {
        jar("upper/Shouty.JAR", "com/acme/upper/Shouty");
        Path wildcard = workspace.resolve("upper").resolve("*");

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(wildcard)).entries();

        assertEquals(2, entries.size());
    }

    @Test
    void expandsABareWildcardAgainstTheWorkingDirectory() {
        EffectiveClasspath classpath = expand(List.of(application()), List.of(Path.of("*")));

        assertEquals(application().toString(), classpath.entries().get(0).display());
        assertTrue(classpath.entries().stream().skip(1)
                .allMatch(entry -> ArchiveLayout.isExpandableArchive(entry.display())));
    }

    @Test
    void keepsTheFirstAppearanceOfARepeatedEntry() {
        Path library = jar("lib/library.jar", "com/acme/lib/Library");
        Path detour = workspace.resolve("lib").resolve("..").resolve("lib").resolve("library.jar");

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(library, detour)).entries();

        assertEquals(List.of(application().toString(), library.toString()), displays(entries));
    }

    @Test
    void insertsAManifestChainImmediatelyAfterItsDeclaringJar() {
        Path chained = jar("lib/chained.jar", "com/acme/chained/Chained");
        Path declaring = chainingJar("lib/declaring.jar", "com/acme/declaring/Declaring", "chained.jar");
        Path last = jar("lib/last.jar", "com/acme/last/Last");

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(declaring, last)).entries();

        assertEquals(
                List.of(application().toString(), declaring.toString(), chained.toString(), last.toString()),
                displays(entries));
    }

    @Test
    void followsAManifestChainRecursively() {
        Path deepest = jar("lib/deepest.jar", "com/acme/deepest/Deepest");
        chainingJar("lib/middle.jar", "com/acme/middle/Middle", "deepest.jar");
        Path first = chainingJar("lib/first.jar", "com/acme/first/First", "middle.jar");

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(first)).entries();

        assertTrue(displays(entries).contains(deepest.toString()), displays(entries).toString());
        assertEquals(4, entries.size());
    }

    @Test
    void stopsAManifestCycleAtTheFirstRepeat() {
        chainingJar("lib/left.jar", "com/acme/left/Left", "right.jar");
        Path right = chainingJar("lib/right.jar", "com/acme/right/Right", "left.jar");

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(right)).entries();

        assertEquals(3, entries.size());
    }

    @Test
    void reportsAManifestEntryThatIsNotThere() {
        Path declaring = chainingJar("lib/declaring.jar", "com/acme/declaring/Declaring", "absent.jar");

        EffectiveClasspath classpath = expand(List.of(application()), List.of(declaring));

        Finding finding = EngineFixture.required(classpath.findings(), "JP2007");
        assertEquals("absent.jar", finding.subject());
        assertEquals(declaring.toString(), finding.artifact().artifact());
        assertEquals(Optional.empty(), finding.artifact().classEntry());
    }

    @Test
    void reportsAManifestEntryThatCannotBeAPathAtAll() {
        String impossible = "broken\0name.jar";
        Path declaring = chainingJar("lib/declaring.jar", "com/acme/declaring/Declaring", impossible);

        EffectiveClasspath classpath = expand(List.of(application()), List.of(declaring));

        assertEquals(impossible, EngineFixture.required(classpath.findings(), "JP2007").subject());
    }

    @Test
    void neverExpandsAManifestWildcard() {
        jar("lib/present.jar", "com/acme/present/Present");
        Path declaring = chainingJar("lib/declaring.jar", "com/acme/declaring/Declaring", "*");

        EffectiveClasspath classpath = expand(List.of(application()), List.of(declaring));

        assertEquals("*", EngineFixture.required(classpath.findings(), "JP2007").subject());
        assertEquals(2, classpath.entries().size());
    }

    /**
     * The manifest is parsed while the classpath is assembled, because a {@code Class-Path} decides what
     * follows an archive, and it is carried on the entry so indexing that archive never parses it again.
     */
    @Test
    void carriesTheManifestItParsedOnTheEntryThatIndexingWillRead() {
        jar("lib/chained.jar", "com/acme/chained/Chained");
        Path declaring = chainingJar("lib/declaring.jar", "com/acme/declaring/Declaring", "chained.jar");
        Path classes = EngineFixture.classDirectory(workspace, "classes",
                EngineFixture.entries("com/acme/dir/Dir.class", EngineFixture.classFile("com/acme/dir/Dir")));

        List<ClasspathEntry> entries = expand(List.of(application()), List.of(declaring, classes)).entries();

        ClasspathEntry archive = entries.get(1);
        assertEquals(declaring.toString(), archive.display());
        assertEquals(List.of("chained.jar"), ArchiveManifest.classPath(archive.manifest()));
        assertEquals(Optional.empty(), entries.get(entries.size() - 1).manifest());
    }

    @Test
    void refusesAnArchiveItCannotReadInTheCallersOwnWordsForIt() {
        Path broken = workspace.resolve("lib").resolve("broken.jar");
        write(broken, "not an archive");
        Path detour = workspace.resolve("lib").resolve("..").resolve("lib").resolve("broken.jar");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> expand(List.of(application()), List.of(detour)));

        assertEquals(ArchiveManifest.UNREADABLE + detour, failure.getMessage());
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private EffectiveClasspath expand(List<Path> applications, List<Path> classpath) {
        return ClasspathExpander.expand(EngineFixture.request(applications, classpath, 17), new ResourceBudget());
    }

    private static List<String> displays(List<ClasspathEntry> entries) {
        return entries.stream().map(ClasspathEntry::display).toList();
    }

    private Path application() {
        return jar("app.jar", "com/acme/App");
    }

    private Path jar(String name, String internalName) {
        return EngineFixture.jar(workspace, name,
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName)));
    }

    private Path chainingJar(String name, String internalName, String classPath) {
        Manifest manifest = EngineFixture.manifest();
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        Map<String, byte[]> entries = EngineFixture.withManifest(
                EngineFixture.entries(internalName + ".class", EngineFixture.classFile(internalName)), manifest);
        return EngineFixture.jar(workspace, name, entries);
    }
}
