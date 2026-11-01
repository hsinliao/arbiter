package io.github.arbiter.server.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Java 8-compatible stop protocol shared by {@code server-stop} and the control CLI. */
public final class ServerStop {
    // Keep this above Spring Boot's default graceful-shutdown phase timeout.
    private static final long TIMEOUT_MILLIS = 35_000L;

    private ServerStop() {
    }

    public static int stop(ServerPaths.Paths paths) {
        try {
            if (!ServerLockProbe.isRunning(paths.lockFile())) {
                System.out.println("arbiter-server: not running");
                return 0;
            }
            // The lock file is authoritative: a PID file may be stale after a crash.
            String pid = ServerLockProbe.read(paths.lockFile());
            if (pid.isEmpty()) {
                pid = ServerLockProbe.read(paths.pidFile());
            }
            if (pid.isEmpty()) {
                System.err.println("arbiter-server: cannot determine the server PID");
                return 2;
            }

            Files.createDirectories(paths.runDirectory());
            Files.write(paths.stopFile(), pid.getBytes(StandardCharsets.UTF_8));

            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline
                    && ServerLockProbe.isRunning(paths.lockFile())) {
                Thread.sleep(200L);
            }
            if (ServerLockProbe.isRunning(paths.lockFile())) {
                System.err.println("arbiter-server: graceful shutdown timed out");
                return 1;
            }
            Files.deleteIfExists(paths.pidFile());
            Files.deleteIfExists(paths.stopFile());
            Files.deleteIfExists(paths.readyFile());
            System.out.println("arbiter-server stopped");
            return 0;
        } catch (IOException failure) {
            System.err.println("arbiter-server: " + failure.getMessage());
            return 1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.err.println("arbiter-server: interrupted while waiting for shutdown");
            return 1;
        }
    }
}
