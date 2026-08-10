package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Reads the positions of one classpath at the same time, and hands them back in classpath order.
 *
 * <p>Reading is the expensive half of a verification and it is embarrassingly parallel: a position is
 * decompressed and parsed without consulting any other, and every conflict between positions is decided
 * afterwards, from the results. So each position is read into an isolated artifact of its own, on
 * whichever thread reaches it first, and the results are collected by position rather than by whoever
 * finished — which is the whole determinism argument. Nothing downstream can tell a parallel scan from a
 * sequential one, because nothing downstream is handed anything an ordered merge did not produce.
 *
 * <p>Parallelism stops at the boundary of one position. An artifact is read as a stream of entries whose
 * cursor is state shared by that read alone, so one position is read by exactly one thread; the
 * concurrency here is across positions and nowhere else. A classpath of a single position skips the pool
 * altogether and is read on the calling thread, because handing one task to one thread only adds a
 * hand-off.
 *
 * <p>A failed read is a failed run. The first failure in classpath order is raised with its own type and
 * its own message — the plumbing that carried it between threads is never what a caller is told about —
 * the positions that have not started are given up on, and the pool is shut down on every path out,
 * that one included.
 */
final class ScanSchedule {
    /** Names every thread a scan runs on, so a stack trace says which phase of a run it belongs to. */
    private static final String WORKER_NAME = "jarproof-scan-";

    private static final String ABANDONED = "This scan was interrupted before every artifact was read";
    private static final int SINGLE_POSITION = 1;

    private final List<ClasspathEntry> entries;
    private final Function<ClasspathEntry, IndexedArtifact> reader;
    private final AtomicInteger started = new AtomicInteger();

    private ScanSchedule(List<ClasspathEntry> entries, Function<ClasspathEntry, IndexedArtifact> reader) {
        this.entries = entries;
        this.reader = reader;
    }

    /**
     * Reads every position of one classpath.
     *
     * @param entries the positions, in the order the target runtime searches them
     * @param reader reads one position into an artifact of its own, consulting no other position
     * @return the artifacts, in classpath order
     * @throws IllegalStateException when a scan is interrupted before every position was read
     */
    static List<IndexedArtifact> scan(
            List<ClasspathEntry> entries, Function<ClasspathEntry, IndexedArtifact> reader) {
        return new ScanSchedule(entries, reader).run();
    }

    private List<IndexedArtifact> run() {
        if (entries.size() <= SINGLE_POSITION) {
            return entries.stream().map(reader).toList();
        }
        ExecutorService pool = Executors.newFixedThreadPool(width(), this::worker);
        try {
            return merged(submitted(pool));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * How many positions are read at once: never more than there are positions to read, and never more
     * than the platform's own view of how many threads a processor-bound pool should run.
     *
     * <p>That view is asked for on each scan rather than settled once and kept, because a count captured
     * while a native binary was being built would describe the machine that built it instead of the
     * machine running it.
     */
    private int width() {
        return Math.min(entries.size(), Math.max(SINGLE_POSITION, ForkJoinPool.getCommonPoolParallelism()));
    }

    private Thread worker(Runnable task) {
        Thread worker = new Thread(task, WORKER_NAME + started.incrementAndGet());
        worker.setDaemon(true);
        return worker;
    }

    private List<Future<IndexedArtifact>> submitted(ExecutorService pool) {
        List<Future<IndexedArtifact>> pending = new ArrayList<>();
        for (ClasspathEntry entry : entries) {
            pending.add(pool.submit(() -> reader.apply(entry)));
        }
        return pending;
    }

    /** Collects the artifacts by position, which is what makes the answer independent of the schedule. */
    private static List<IndexedArtifact> merged(List<Future<IndexedArtifact>> pending) {
        List<IndexedArtifact> read = new ArrayList<>();
        for (Future<IndexedArtifact> position : pending) {
            read.add(settled(position, pending));
        }
        return List.copyOf(read);
    }

    private static IndexedArtifact settled(
            Future<IndexedArtifact> position, List<Future<IndexedArtifact>> pending) {
        try {
            return position.get();
        } catch (ExecutionException failure) {
            throw abandoning(pending, surfaced(failure.getCause()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw abandoning(pending, new IllegalStateException(ABANDONED, interrupted));
        }
    }

    /** Gives up on the positions still outstanding, and returns the failure that ended the scan. */
    private static RuntimeException abandoning(
            List<Future<IndexedArtifact>> pending, RuntimeException failure) {
        pending.forEach(position -> position.cancel(false));
        return failure;
    }

    /**
     * The failure one position raised, as the failure it was raised as.
     *
     * <p>Reading an artifact refuses a run with an unchecked failure and nothing else — an unusable
     * input, a breached ceiling, an unreadable file — so a caller is handed exactly that one, and an
     * error that escaped a thread is past reporting either way.
     */
    private static RuntimeException surfaced(Throwable cause) {
        if (cause instanceof RuntimeException raised) {
            return raised;
        }
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        return new IllegalStateException(cause);
    }
}
