package io.github.arbiter.client;

import io.github.arbiter.api.ApiHeaders;

import tools.jackson.databind.ObjectMapper;
import io.github.arbiter.api.uds.UdsRequest;
import io.github.arbiter.api.uds.UdsResponse;
import io.github.arbiter.client.config.UnixSocketTransportConfig;
import io.github.arbiter.client.transport.TransportRequest;
import io.github.arbiter.client.transport.TransportResponse;
import io.github.arbiter.client.transport.UnixSocketApiTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnixSocketApiTransportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsAndReceivesFramedJson(@TempDir Path directory) throws Exception {
        Path socket = directory.resolve("fake.sock");
        CompletableFuture<UdsRequest> received = new CompletableFuture<>();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Thread server = new Thread(() -> serve(socket, received, ready));
        server.start();
        ready.get(2, TimeUnit.SECONDS);

        try (UnixSocketApiTransport transport = new UnixSocketApiTransport(
                new UnixSocketTransportConfig(socket, Duration.ofSeconds(2)))) {
            TransportResponse response = transport.execute(new TransportRequest(
                    "GET", "/api/v1/status", Map.of(ApiHeaders.ARBITER_TOKEN, "token"), null));
            assertEquals(200, response.status());
            assertEquals("{\"state\":\"UP\"}", response.body());
        }

        UdsRequest request = received.get(2, TimeUnit.SECONDS);
        assertEquals("GET", request.method());
        assertEquals("/api/v1/status", request.path());
        assertEquals("token", request.headers().get(ApiHeaders.ARBITER_TOKEN));
        server.join(2000);
    }

    private void serve(Path socket, CompletableFuture<UdsRequest> received,
                       CompletableFuture<Void> ready) {
        try (ServerSocketChannel server = ServerSocketChannel.open(
                StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            ready.complete(null);
            try (SocketChannel channel = server.accept()) {
                UdsRequest request = mapper.readValue(readFrame(channel),
                        UdsRequest.class);
                received.complete(request);
                UdsResponse response = new UdsResponse(200,
                        Map.of("Content-Type", "application/json"),
                        "{\"state\":\"UP\"}");
                writeFrame(channel, mapper.writeValueAsBytes(response));
            }
        } catch (Exception failure) {
            ready.completeExceptionally(failure);
            received.completeExceptionally(failure);
        }
    }

    private static byte[] readFrame(SocketChannel channel) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(4);
        readFully(channel, header);
        header.flip();
        int length = header.getInt();
        ByteBuffer payload = ByteBuffer.allocate(length);
        readFully(channel, payload);
        return payload.array();
    }

    private static void writeFrame(SocketChannel channel, byte[] payload)
            throws Exception {
        ByteBuffer frame = ByteBuffer.allocate(4 + payload.length);
        frame.putInt(payload.length);
        frame.put(payload);
        frame.flip();
        while (frame.hasRemaining()) {
            channel.write(frame);
        }
    }

    private static void readFully(SocketChannel channel, ByteBuffer buffer)
            throws Exception {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new EOFException();
            }
        }
    }
}
