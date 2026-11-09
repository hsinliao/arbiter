package io.github.arbiter.client.transport;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.github.arbiter.api.uds.UdsRequest;
import io.github.arbiter.api.uds.UdsResponse;
import io.github.arbiter.client.config.UnixSocketTransportConfig;
import io.github.arbiter.core.exception.NetworkException;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Unix-domain socket transport. Frame format is a 4-byte big-endian length
 * followed by a JSON UdsRequest/UdsResponse envelope.
 */
public final class UnixSocketApiTransport implements ApiTransport {
    private static final int LENGTH_BYTES = Integer.BYTES;
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private final UnixSocketTransportConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService timeoutExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "arbiter-uds-timeout");
                thread.setDaemon(true);
                return thread;
            });

    public UnixSocketApiTransport(UnixSocketTransportConfig config) {
        this.config = config;
    }

    @Override
    public TransportResponse execute(TransportRequest request)
            throws IOException, InterruptedException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ScheduledFuture<?> timeout = timeoutExecutor.schedule(() -> {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // Closing the channel is the timeout signal.
                }
            }, config.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
            try {
                channel.connect(UnixDomainSocketAddress.of(config.socketPath()));
                UdsRequest envelope = new UdsRequest(request.method(), request.path(),
                        request.headers(), request.body());
                writeFrame(channel, mapper.writeValueAsBytes(envelope));
                UdsResponse response = mapper.readValue(readFrame(channel),
                        UdsResponse.class);
                return new TransportResponse(response.status(), response.headers(),
                        response.body());
            } finally {
                timeout.cancel(false);
            }
        } catch (IOException | JacksonException failure) {
            throw new NetworkException(failure);
        }
    }

    @Override
    public void close() {
        timeoutExecutor.shutdownNow();
    }

    private static byte[] readFrame(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(LENGTH_BYTES);
        readFully(channel, header);
        header.flip();
        int length = header.getInt();
        if (length < 0 || length > MAX_FRAME_BYTES) {
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
}
