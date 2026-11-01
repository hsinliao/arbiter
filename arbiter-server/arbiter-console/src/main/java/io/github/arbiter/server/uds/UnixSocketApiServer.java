package io.github.arbiter.server.uds;

import tools.jackson.databind.ObjectMapper;
import io.github.arbiter.api.uds.UdsRequest;
import io.github.arbiter.api.uds.UdsResponse;
import io.github.arbiter.server.config.ServerConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unix-domain socket adapter. Frame format is a 4-byte big-endian length
 * followed by a JSON UdsRequest/UdsResponse envelope.
 */
public final class UnixSocketApiServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(UnixSocketApiServer.class);
    private static final int LENGTH_BYTES = Integer.BYTES;
    private static final int MAX_EXTRA_FRAME_BYTES = 65_536;

    private final ServerConfig config;
    private final UdsRequestDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path socketPath;
    private final int maxFrameBytes;
    private final ExecutorService executor;
    private final ServerSocketChannel serverChannel;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Thread acceptThread;

    public UnixSocketApiServer(ServerConfig config,
                               UdsRequestDispatcher dispatcher,
                               boolean replaceExisting) throws IOException {
        this.config = config;
        this.dispatcher = dispatcher;
        this.socketPath = config.transport().socketPath();
        if (socketPath == null) {
            throw new IllegalArgumentException(
                    "unix transport requires arbiter.home; start the server through bin/server-start");
        }
        this.maxFrameBytes = config.maxRequestBytes() + MAX_EXTRA_FRAME_BYTES;
        Path parent = socketPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(socketPath)) {
            if (!replaceExisting) {
                throw new IOException("Unix socket already exists: " + socketPath);
            }
            Files.deleteIfExists(socketPath);
        }
        this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        this.serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
        hideSocket(socketPath);
        this.executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "arbiter-uds-worker");
            thread.setDaemon(false);
            return thread;
        });
    }

    public void start() {
        acceptThread = new Thread(this::acceptLoop, "arbiter-uds-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public String description() {
        return "unix:" + socketPath + config.apiPrefix();
    }

    public Path socketPath() {
        return socketPath;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                serverChannel.close();
            } catch (IOException ignored) {
                // Shutdown is best-effort.
            }
            if (acceptThread != null) {
                acceptThread.interrupt();
            }
            executor.shutdownNow();
            try {
                Files.deleteIfExists(socketPath);
            } catch (IOException ignored) {
                // Stale socket cleanup is best-effort.
            }
        }
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                SocketChannel channel = serverChannel.accept();
                executor.submit(() -> handle(channel));
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException failure) {
                if (!closed.get()) {
                    // A transient accept failure should not spin; log and stop.
                    LOG.error("UDS accept failed: {}", failure.getMessage());
                }
                return;
            }
        }
    }

    private void handle(SocketChannel channel) {
        try (channel) {
            UdsRequest request = mapper.readValue(readFrame(channel), UdsRequest.class);
            UdsResponse response = dispatcher.handle(request);
            writeResponse(channel, response);
        } catch (IOException | RuntimeException ignored) {
            // Malformed frame or disconnected peer: close this connection.
        }
    }

    private void writeResponse(SocketChannel channel, UdsResponse response)
            throws IOException {
        writeFrame(channel, mapper.writeValueAsBytes(response));
    }

    private byte[] readFrame(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(LENGTH_BYTES);
        readFully(channel, header);
        header.flip();
        int length = header.getInt();
        if (length < 0 || length > maxFrameBytes) {
            throw new IOException("Invalid UDS frame length: " + length);
        }
        ByteBuffer payload = ByteBuffer.allocate(length);
        readFully(channel, payload);
        return payload.array();
    }

    private static void writeFrame(SocketChannel channel, byte[] payload)
            throws IOException {
        ByteBuffer frame = ByteBuffer.allocate(LENGTH_BYTES + payload.length);
        frame.putInt(payload.length);
        frame.put(payload);
        frame.flip();
        while (frame.hasRemaining()) {
            channel.write(frame);
        }
    }

    private static void readFully(SocketChannel channel, ByteBuffer buffer)
            throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new EOFException("UDS peer closed the connection");
            }
        }
    }

    private static void hideSocket(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows and some filesystems do not expose POSIX permissions.
        }
    }
}
