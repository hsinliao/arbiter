package io.github.arbiter.server.control;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Java 8-compatible lock/readiness probes used by the control plane. */
public final class ServerLockProbe {
    private ServerLockProbe() {
    }

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

    public static String read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    }
}
