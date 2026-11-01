package io.github.arbiter.cli;

import io.github.arbiter.api.config.ArbiterKeys;

import io.github.arbiter.core.exception.ServerControlException;
import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.service.ManagedServerStatus;
import io.github.arbiter.core.service.ServerControlService;
import io.github.arbiter.core.service.ServerOperationResult;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessServerControlService implements ServerControlService {
    private final Path appHome;

    ProcessServerControlService(Path appHome) {
        this.appHome = appHome;
    }

    @Override
    public ServerOperationResult start(String debugAddress) {
        ManagedServerStatus current = status();
        if (current.running()) {
            return new ServerOperationResult("start", current.pid(), true);
        }
        runScript("server-start", debugArgs(debugAddress),
                Duration.ofSeconds(45));
        ManagedServerStatus started = status();
        if (!started.running()) {
            throw new ServerControlException("err.server.control.startFailed");
        }
        return new ServerOperationResult("start", started.pid(), false);
    }

    @Override
    public ServerOperationResult restart(String debugAddress) {
        stop();
        runScript("server-start", debugArgs(debugAddress),
                Duration.ofSeconds(45));
        ManagedServerStatus started = status();
        if (!started.running()) {
            throw new ServerControlException("err.server.control.startFailed");
        }
        return new ServerOperationResult("restart", started.pid(), false);
    }

    @Override
    public ServerOperationResult stop() {
        ManagedServerStatus current = status();
        if (!current.running()) {
            return new ServerOperationResult("stop", 0, false);
        }
        runScript("server-stop", List.of(), Duration.ofSeconds(30));
        if (status().running()) {
            throw new ServerControlException("err.server.control.stopFailed");
        }
        return new ServerOperationResult("stop", current.pid(), false);
    }

    /**
     * The PID file alone is not proof of a running server: PIDs are reused and
     * stale files survive crashes. The server holds an exclusive lock on
     * {@code run/.server.lock} for its whole lifetime, so probing that lock is
     * the authoritative liveness check.
     */
    @Override
    public ManagedServerStatus status() {
        if (appHome == null) {
            return new ManagedServerStatus(false, 0);
        }
        Path runDirectory = appHome.resolve("run");
        Path lockFile = runDirectory.resolve(".server.lock");
        Path pidFile = runDirectory.resolve(".server.pid");
        if (!Files.isRegularFile(lockFile)) {
            return new ManagedServerStatus(false, 0);
        }
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock probe = null;
            try {
                probe = channel.tryLock();
            } catch (OverlappingFileLockException heldByThisJvm) {
                return runningStatus(lockFile, pidFile);
            }
            if (probe != null) {
                probe.release();
                return new ManagedServerStatus(false, 0);
            }
            return runningStatus(lockFile, pidFile);
        } catch (IOException | RuntimeException unreadableState) {
            return new ManagedServerStatus(false, 0);
        }
    }

    private static ManagedServerStatus runningStatus(Path lockFile, Path pidFile) {
        long pid = readPid(lockFile);
        if (pid <= 0) {
            pid = readPid(pidFile);
        }
        if (pid <= 0) {
            return new ManagedServerStatus(true, 0);
        }
        boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        return new ManagedServerStatus(alive, alive ? pid : 0);
    }

    private static long readPid(Path file) {
        try {
            return Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException invalidPid) {
            return 0;
        }
    }

    private static List<String> debugArgs(String debugAddress) {
        return debugAddress == null || debugAddress.isBlank()
                ? List.of()
                : List.of("--debug=" + debugAddress);
    }

    private void runScript(String name, List<String> arguments, Duration timeout) {
        if (appHome == null) {
            throw new ServerControlException("err.server.control.unavailable");
        }
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
        Path script = appHome.resolve("bin").resolve(name + (windows ? ".bat" : ""));
        if (!Files.isRegularFile(script)) {
            throw new ServerControlException("err.server.control.unavailable");
        }
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/s");
            command.add("/c");
            StringBuilder line = new StringBuilder(windowsQuote(script.toString()));
            for (String argument : arguments) {
                line.append(' ').append(windowsQuote(argument));
            }
            command.add("\"" + line + "\"");
        } else {
            command.add("sh");
            command.add(script.toString());
            command.addAll(arguments);
        }
        try {
            Path logDirectory = appHome.resolve("logs");
            Files.createDirectories(logDirectory);
            Path controlLog = logDirectory.resolve("cli.log");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(controlLog.toFile()))
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                throw new ServerControlException("err.server.control.startFailed");
            }
            if (process.exitValue() != 0) {
                String key = name.endsWith("stop")
                        ? "err.server.control.stopFailed"
                        : "err.server.control.startFailed";
                throw new ServerControlException(key);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UserAbortedException();
        } catch (IOException ioFailure) {
            throw new ServerControlException("err.server.control.unavailable");
        }
    }

    static Path detectAppHome() {
        String configured = System.getProperty(ArbiterKeys.SystemProperty.HOME);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        try {
            URI location = CliApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            Path current = Path.of(location).toAbsolutePath().normalize();
            if (Files.isRegularFile(current)) {
                current = current.getParent();
            }
            while (current != null) {
                Path marker = current.resolve("bin").resolve("server-start.bat");
                Path unixMarker = current.resolve("bin").resolve("server-start");
                if (Files.isRegularFile(marker) || Files.isRegularFile(unixMarker)) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {
            // Unavailable lifecycle management is reported when a server command is used.
        }
        return null;
    }

    private static String windowsQuote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
