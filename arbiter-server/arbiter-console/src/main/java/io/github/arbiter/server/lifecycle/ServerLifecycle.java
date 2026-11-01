package io.github.arbiter.server.lifecycle;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process lifecycle concerns: PID/stop/lock files and graceful shutdown.
 */
public final class ServerLifecycle {
    private ServerLifecycle() {
    }

    public static Path resolvePath(String value) {
        return value == null || value.isBlank()
                ? null : Path.of(value).toAbsolutePath().normalize();
    }

    public static void writePidFile(Path path, long processId) throws IOException {
        if (path == null) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, Long.toString(processId), StandardCharsets.UTF_8);
        markHidden(path);
    }

    public static ScheduledExecutorService watchStopSignal(Path stopFile, long processId,
                                                              CountDownLatch stopped) {
        ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "arbiter-server-stop-watcher");
                    thread.setDaemon(true);
                    return thread;
                });
        if (stopFile == null) {
            return watcher;
        }
        AtomicBoolean delivered = new AtomicBoolean();
        watcher.scheduleWithFixedDelay(() -> {
            try {
                if (!delivered.get() && Files.isRegularFile(stopFile)
                        && Files.readString(stopFile, StandardCharsets.UTF_8).trim()
                        .equals(Long.toString(processId))) {
                    if (delivered.compareAndSet(false, true)) {
                        stopped.countDown();
                    }
                }
            } catch (IOException ignored) {
                // A partial or transient stop file is ignored.
            }
        }, 250, 250, TimeUnit.MILLISECONDS);
        return watcher;
    }

    /**
     * The lock file is the instance identity. A PID file can be stale or point
     * at a reused PID; a live lock can only be held by a server instance.
     */
    public static boolean isRunning(Path lockPath) {
        if (lockPath == null || !Files.isRegularFile(lockPath)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock probe = null;
            try {
                probe = channel.tryLock();
            } catch (OverlappingFileLockException heldByThisJvm) {
                return true;
            }
            if (probe != null) {
                probe.release();
                return false;
            }
            return true;
        } catch (IOException unreadableState) {
            return false;
        }
    }

    public static LockedInstance acquireLock(Path lockPath) throws IOException {
        if (lockPath == null) {
            return null;
        }
        Path parent = lockPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new IllegalStateException(
                        "A arbiter-server instance is already running");
            }
            FileLock held = lock;
            channel.position(0);
            channel.truncate(0);
            channel.write(java.nio.ByteBuffer.wrap(
                    Long.toString(ProcessHandle.current().pid())
                            .getBytes(StandardCharsets.US_ASCII)));
            channel.force(true);
            markHidden(lockPath);
            return new LockedInstance(channel, held);
        } catch (OverlappingFileLockException alreadyLocked) {
            channel.close();
            throw new IllegalStateException(
                    "A arbiter-server instance is already running", alreadyLocked);
        }
    }

    public static void closeQuietly(FileLock lock, FileChannel channel) {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
            // Lock cleanup is best-effort during shutdown.
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
            // Lock cleanup is best-effort during shutdown.
        }
    }

    public static void cleanup(Path pidPath, Path stopPath) {
        try {
            if (pidPath != null) {
                Files.deleteIfExists(pidPath);
            }
            if (stopPath != null) {
                Files.deleteIfExists(stopPath);
            }
        } catch (IOException ignored) {
            // Runtime state cleanup is best-effort.
        }
    }

    private static void markHidden(Path path) {
        try {
            Files.setAttribute(path, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Dot-prefixed state files are already hidden on Unix-like systems.
        }
    }

    public record LockedInstance(FileChannel channel, FileLock lock) {
    }
}
