package io.github.arbiter.server.runtime;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.server.control.ServerPaths;
import io.github.arbiter.server.control.ServerVersion;
import io.github.arbiter.server.config.ServerConfig;
import io.github.arbiter.server.lifecycle.ServerLifecycle;
import io.github.arbiter.server.uds.UdsRequestDispatcher;
import io.github.arbiter.server.uds.UnixSocketApiServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerRuntime implements ApplicationListener<ApplicationReadyEvent>,
        AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ServerRuntime.class);

    private final ServerConfig config;
    private final ServerPaths.Paths paths;
    private final ServerLifecycle.LockedInstance lock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final UnixSocketApiServer unixServer;
    private volatile ScheduledExecutorService stopWatcher;
    private volatile Thread stopThread;

    public ServerRuntime(ServerConfig config, UdsRequestDispatcher dispatcher)
            throws Exception {
        Path home = ArbiterHome.root();
        if (home == null) {
            throw new IllegalStateException(
                    "arbiter.home is not set; start the server through bin/server-start");
        }
        this.config = config;
        this.paths = ServerPaths.under(home);
        logRuntimeEnvironment(home);

        Files.createDirectories(paths.runDirectory());
        this.lock = ServerLifecycle.acquireLock(paths.lockFile());
        Files.deleteIfExists(paths.stopFile());
        Files.deleteIfExists(paths.readyFile());
        UnixSocketApiServer server = null;
        try {
            if (config.transport().unixEnabled()) {
                server = new UnixSocketApiServer(config, dispatcher, lock != null);
                server.start();
            }
        } catch (Exception failure) {
            if (server != null) {
                server.close();
            }
            ServerLifecycle.cleanup(paths.pidFile(), paths.stopFile());
            try {
                Files.deleteIfExists(paths.readyFile());
            } catch (Exception ignored) {
                // Startup cleanup is best-effort.
            }
            ServerLifecycle.closeQuietly(
                    lock == null ? null : lock.lock(),
                    lock == null ? null : lock.channel());
            throw failure;
        }
        this.unixServer = server;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        try {
            long processId = ProcessHandle.current().pid();
            ServerLifecycle.writePidFile(paths.pidFile(), processId);
            if (unixServer != null) {
                LOG.info("listening on {}", unixServer.description());
            }
            if (config.transport().httpEnabled()) {
                LOG.info("listening on {}://{}:{}{}",
                        config.tls().enabled() ? "https" : "http",
                        config.host(), config.port(), config.apiPrefix());
            }
            CountDownLatch stopped = new CountDownLatch(1);
            stopWatcher = ServerLifecycle.watchStopSignal(
                    paths.stopFile(), processId, stopped);
            stopThread = new Thread(() -> {
                try {
                    stopped.await();
                    LOG.info("stop requested");
                    context.close();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "arbiter-server-stop-controller");
            stopThread.setDaemon(true);
            stopThread.start();
            ServerLifecycle.writePidFile(paths.readyFile(), processId);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to complete server startup", failure);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        LOG.info("shutting down");
        if (stopWatcher != null) {
            stopWatcher.shutdownNow();
        }
        if (unixServer != null) {
            unixServer.close();
        }
        ServerLifecycle.cleanup(paths.pidFile(), paths.stopFile());
        try {
            Files.deleteIfExists(paths.readyFile());
        } catch (Exception ignored) {
            // Shutdown cleanup is best-effort.
        }
        ServerLifecycle.closeQuietly(
                lock == null ? null : lock.lock(),
                lock == null ? null : lock.channel());
        LOG.info("stopped");
    }

    private static void logRuntimeEnvironment(Path home) {
        LOG.info("arbiter-server {} starting", ServerVersion.version());
        LOG.info("java.home={}", property("java.home"));
        LOG.info("java.version={}", property("java.version"));
        LOG.info("os.name={}", property("os.name"));
        LOG.info("os.version={}", property("os.version"));
        LOG.info("os.arch={}", property("os.arch"));
        LOG.info("arbiter.home={}", home);
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? "<unknown>" : value;
    }
}
