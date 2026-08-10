package sh.zolt.jarproof.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The contract a parallel scan owes the rest of the engine: classpath order, and failures that arrive
 * as themselves.
 *
 * <p>Every position here is read by a stub, because what is under test is the schedule and not any
 * reader — a stub can fail on demand, block on demand, and say which thread it ran on. The one test
 * that reads real archives is the one that has to: merging by position rather than by whoever finished
 * is the whole determinism argument, and it is only worth asserting against artifacts that genuinely
 * finish out of order.
 */
final class ScanScheduleTest {
    private static final int MANY_LIBRARIES = 24;
    private static final int MANY_POSITIONS = 256;
    private static final int PAIR = 2;
    private static final int EARLIER = 3;
    private static final int LATER = 7;
    private static final int HELD_SECONDS = 30;
    private static final String SHARED = "com/acme/shared/Shared";
    private static final String LIBRARY_PACKAGE = "com/acme/lib/";
    private static final String CLASS_SUFFIX = ".class";
    private static final String BREACHED = "a ceiling this position crossed";
    private static final String UNUSABLE = "an input this position could not use";
    private static final String UNREADABLE = "a file this position could not open";
    private static final String WORKER_PREFIX = "jarproof-scan-";

    @TempDir
    Path workspace;

    @Test
    void mergesEveryPositionInClasspathOrderWhicheverThreadReachedItFirst() {
        List<Path> libraries = libraries();

        List<String> read = displays(catalog(libraries, new ResourceBudget()));

        assertEquals(paths(libraries), read);
        assertEquals(read, displays(catalog(libraries, new ResourceBudget())));
    }

    @Test
    void keepsTheShadowedCopiesOfOneClassInClasspathOrderToo() {
        List<Path> libraries = libraries();

        ArtifactCatalog catalog = catalog(libraries, new ResourceBudget());

        assertEquals(
                paths(libraries),
                catalog.declarations().get(SHARED).stream().map(ClassDeclaration::artifactPath).toList());
    }

    @Test
    void readsALonePositionOnTheCallingThreadInsteadOfThroughAPool() {
        List<String> threads = new ArrayList<>();

        List<IndexedArtifact> read = ScanSchedule.scan(positions(1), entry -> recorded(threads, entry));

        assertEquals(List.of(Thread.currentThread().getName()), threads);
        assertEquals(1, read.size());
    }

    @Test
    void readsSeveralPositionsOnNamedWorkersAndNotOnTheCallingThread() {
        Set<String> threads = ConcurrentHashMap.newKeySet();

        List<IndexedArtifact> read = ScanSchedule.scan(
                positions(MANY_LIBRARIES), entry -> recorded(threads, entry));

        assertEquals(MANY_LIBRARIES, read.size());
        assertFalse(threads.contains(Thread.currentThread().getName()), threads::toString);
        assertTrue(threads.stream().allMatch(name -> name.startsWith(WORKER_PREFIX)), threads::toString);
    }

    @Test
    void raisesTheFailureOfTheEarliestFailingPositionWithItsOwnTypeAndMessage() {
        List<ClasspathEntry> entries = positions(MANY_POSITIONS);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> ScanSchedule.scan(entries, ScanScheduleTest::failing));

        assertEquals(BREACHED, failure.getMessage());
    }

    @Test
    void raisesAnUnreadableArtifactAsTheUncheckedFailureItWasRaisedAs() {
        List<ClasspathEntry> entries = positions(PAIR);

        UncheckedIOException failure = assertThrows(
                UncheckedIOException.class,
                () -> ScanSchedule.scan(entries, entry -> {
                    throw new UncheckedIOException(new IOException(UNREADABLE));
                }));

        assertEquals(UNREADABLE, failure.getCause().getMessage());
    }

    /** An error that left a worker is past reporting, so it is re-raised rather than wrapped in a run. */
    @Test
    void raisesAnErrorThatEscapedAWorkerAsThatSameError() {
        List<ClasspathEntry> entries = positions(PAIR);

        AssertionError failure = assertThrows(AssertionError.class, () -> ScanSchedule.scan(entries, entry -> {
            throw new AssertionError(UNUSABLE);
        }));

        assertEquals(UNUSABLE, failure.getMessage());
    }

    @Test
    void givesUpOnThePositionsItHadNotStartedWhenOneFailed() {
        CountDownLatch held = new CountDownLatch(1);
        List<String> started = Collections.synchronizedList(new ArrayList<>());
        List<ClasspathEntry> entries = positions(MANY_POSITIONS);

        assertThrows(
                IllegalStateException.class,
                () -> ScanSchedule.scan(entries, entry -> blocking(held, started, entry)));

        assertTrue(started.size() < MANY_POSITIONS, () -> String.valueOf(started.size()));
    }

    @Test
    void abandonsAScanTheCallerWasInterruptedDuring() {
        CountDownLatch held = new CountDownLatch(1);
        List<ClasspathEntry> entries = positions(PAIR);
        Thread.currentThread().interrupt();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> ScanSchedule.scan(entries, entry -> waiting(held, entry)));

        assertTrue(Thread.interrupted(), failure::getMessage);
        assertTrue(failure.getMessage().startsWith("This scan was interrupted"), failure::getMessage);
        assertEquals(InterruptedException.class, failure.getCause().getClass());
    }

    private static IndexedArtifact failing(ClasspathEntry entry) {
        if (entry.display().equals(name(EARLIER))) {
            throw new IllegalStateException(BREACHED);
        }
        if (entry.display().equals(name(LATER))) {
            throw new IllegalArgumentException(UNUSABLE);
        }
        return artifact(entry);
    }

    /** Fails the first position and holds every other one, so what was never started stays observable. */
    private static IndexedArtifact blocking(CountDownLatch held, List<String> started, ClasspathEntry entry) {
        started.add(entry.display());
        if (entry.display().equals(name(0))) {
            throw new IllegalStateException(BREACHED);
        }
        return waiting(held, entry);
    }

    private static IndexedArtifact waiting(CountDownLatch held, ClasspathEntry entry) {
        try {
            held.await(HELD_SECONDS, TimeUnit.SECONDS);
            return artifact(entry);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static IndexedArtifact recorded(Collection<String> threads, ClasspathEntry entry) {
        threads.add(Thread.currentThread().getName());
        return artifact(entry);
    }

    private static ArtifactCatalog catalog(List<Path> libraries, ResourceBudget budget) {
        return ArtifactCatalog.read(
                EngineFixture.request(
                        List.of(libraries.get(0)), libraries.subList(1, libraries.size()), 17),
                budget);
    }

    private List<Path> libraries() {
        List<Path> libraries = new ArrayList<>();
        for (int index = 0; index < MANY_LIBRARIES; index++) {
            libraries.add(library(index));
        }
        return List.copyOf(libraries);
    }

    /** One small archive that declares a class of its own and its own copy of one shared class. */
    private Path library(int index) {
        String simpleName = "Library" + index;
        Map<String, byte[]> entries = EngineFixture.entries(
                SHARED + CLASS_SUFFIX, EngineFixture.classFile(SHARED));
        entries.put(
                LIBRARY_PACKAGE + simpleName + CLASS_SUFFIX,
                EngineFixture.classFile(LIBRARY_PACKAGE + simpleName));
        return EngineFixture.jar(workspace, simpleName + ".jar", entries);
    }

    private static List<ClasspathEntry> positions(int count) {
        List<ClasspathEntry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            entries.add(new ClasspathEntry(
                    name(index),
                    Path.of(name(index)),
                    EntryKind.ARCHIVE,
                    ClasspathOrigin.CLASSPATH,
                    Optional.empty(),
                    Optional.empty()));
        }
        return List.copyOf(entries);
    }

    private static IndexedArtifact artifact(ClasspathEntry entry) {
        return new IndexedArtifact(entry, List.of(), Optional.empty(), List.of(), List.of());
    }

    private static String name(int index) {
        return "lib/position-" + index + ".jar";
    }

    private static List<String> displays(ArtifactCatalog catalog) {
        return catalog.artifacts().stream().map(artifact -> artifact.entry().display()).toList();
    }

    private static List<String> paths(List<Path> libraries) {
        return libraries.stream().map(Path::toString).toList();
    }
}
