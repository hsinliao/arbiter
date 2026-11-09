package io.github.arbiter.server.uds;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.uds.UdsRequest;
import io.github.arbiter.api.uds.UdsResponse;
import io.github.arbiter.server.auth.InMemoryUserRepository;
import io.github.arbiter.server.auth.LoginRateLimiter;
import io.github.arbiter.server.auth.MutableUserRepository;
import io.github.arbiter.server.auth.TokenService;
import io.github.arbiter.server.auth.UserAccount;
import io.github.arbiter.server.config.ServerConfig;
import io.github.arbiter.server.config.ServerTransport;
import io.github.arbiter.server.service.AuthService;
import io.github.arbiter.server.service.QueryService;
import io.github.arbiter.server.service.StatusService;
import io.github.arbiter.server.service.UserService;
import io.github.arbiter.server.tls.TlsSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnixSocketApiServerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void servesTheSameApiAsHttpOverAFramedUnixSocket(@TempDir Path directory)
            throws Exception {
        Path socket = directory.resolve("server.sock");
        ServerTransport transport = new ServerTransport(
                ServerTransport.Mode.UNIX, socket);
        ServerConfig config = new ServerConfig(
                "127.0.0.1", 0, "/api/v1", Duration.ofHours(1),
                4096, 2, Duration.ofMinutes(1), TlsSettings.disabled(), transport);
        MutableUserRepository users = new InMemoryUserRepository(List.of(
                UserAccount.create("alice", "correct-password", "reader", true)));
        Clock clock = Clock.systemUTC();
        TokenService tokens = new TokenService(SECRET, config.tokenTtl(), clock);
        AuthService auth = new AuthService(users, tokens,
                new LoginRateLimiter(config.loginMaxFailures(),
                        config.loginWindow(), clock));
        UdsRequestDispatcher dispatcher = new UdsRequestDispatcher(config, auth,
                new UserService(users), new StatusService(clock), new QueryService());

        try (UnixSocketApiServer server = new UnixSocketApiServer(
                config, dispatcher, true)) {
            server.start();
            assertTrue(Files.exists(socket));
            assertEquals("unix:" + socket + "/api/v1", server.description());

            UdsResponse login = call(socket, new UdsRequest("POST",
                    "/api/v1" + ApiRoutes.LOGIN, Map.of(),
                    "{\"username\":\"alice\",\"password\":\"correct-password\"}"));
            assertEquals(200, login.status(), login.body());
            String token = mapper.readTree(login.body()).path("token").asText();
            assertFalse(token.isBlank());
            assertTrue(Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.getName().equals("arbiter-uds-accept")));
            assertTrue(Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.getName()
                            .matches("arbiter-uds-worker-\\d+")));

            UdsResponse status = call(socket, new UdsRequest("GET",
                    "/api/v1" + ApiRoutes.STATUS,
                    Map.of(ApiHeaders.ARBITER_TOKEN, token), null));
            assertEquals(200, status.status());
            JsonNode statusBody = mapper.readTree(status.body());
            assertEquals("UP", statusBody.path("state").asText());

            UdsResponse query = call(socket, new UdsRequest("GET",
                    "/api/v1" + ApiRoutes.QUERY + "?name=demo",
                    Map.of(ApiHeaders.ARBITER_TOKEN, token), null));
            assertEquals(200, query.status());
            assertEquals("resource:demo", mapper.readTree(query.body())
                    .path("value").asText());

            UdsResponse missingToken = call(socket, new UdsRequest("GET",
                    "/api/v1" + ApiRoutes.STATUS, Map.of(), null));
            assertEquals(401, missingToken.status());
        }
        assertFalse(Files.exists(socket));
    }

    private UdsResponse call(Path socket, UdsRequest request) throws Exception {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            byte[] payload = mapper.writeValueAsBytes(request);
            ByteBuffer frame = ByteBuffer.allocate(4 + payload.length);
            frame.putInt(payload.length);
            frame.put(payload);
            frame.flip();
            while (frame.hasRemaining()) {
                channel.write(frame);
            }

            ByteBuffer header = ByteBuffer.allocate(4);
            readFully(channel, header);
            header.flip();
            int length = header.getInt();
            ByteBuffer response = ByteBuffer.allocate(length);
            readFully(channel, response);
            return mapper.readValue(response.array(), UdsResponse.class);
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
