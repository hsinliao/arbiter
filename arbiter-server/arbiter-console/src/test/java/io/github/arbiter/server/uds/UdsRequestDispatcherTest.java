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
import io.github.arbiter.server.service.AuthService;
import io.github.arbiter.server.service.QueryService;
import io.github.arbiter.server.service.StatusService;
import io.github.arbiter.server.service.UserService;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UdsRequestDispatcherTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void routesAndAuthenticatesThroughTheServiceLayer() throws Exception {
        UdsRequestDispatcher dispatcher = dispatcher(new InMemoryUserRepository(List.of(
                UserAccount.create("alice", "correct-password", "reader", true))));

        UdsResponse login = dispatcher.handle(new UdsRequest("POST",
                "/api/v1" + ApiRoutes.LOGIN, Map.of(),
                "{\"username\":\"alice\",\"password\":\"correct-password\"}"));
        assertEquals(200, login.status());
        String token = mapper.readTree(login.body()).path("token").asText();

        UdsResponse status = dispatcher.handle(new UdsRequest("GET",
                "/api/v1" + ApiRoutes.STATUS,
                Map.of(ApiHeaders.ARBITER_TOKEN, token), null));
        assertEquals(200, status.status());
        assertEquals("UP", mapper.readTree(status.body()).path("state").asText());

        UdsResponse query = dispatcher.handle(new UdsRequest("GET",
                "/api/v1" + ApiRoutes.QUERY + "?name=demo",
                Map.of(ApiHeaders.ARBITER_TOKEN, token), null));
        assertEquals(200, query.status());
        assertEquals("resource:demo", mapper.readTree(query.body()).path("value").asText());
    }

    @Test
    void methodAndAuthenticationFailuresUseTheApiContract() {
        UdsRequestDispatcher dispatcher = dispatcher(
                new InMemoryUserRepository(List.of()));

        assertEquals(405, dispatcher.handle(new UdsRequest("GET",
                "/api/v1" + ApiRoutes.LOGIN, Map.of(), null)).status());
        UdsResponse unauthenticated = dispatcher.handle(new UdsRequest("GET",
                "/api/v1" + ApiRoutes.STATUS, Map.of(), null));
        assertEquals(401, unauthenticated.status());
        assertEquals("AUTH_REQUIRED", parse(unauthenticated).path("code").asText());
    }

    @Test
    void rejectsBodiesAboveTheConfiguredLimit() {
        UdsRequestDispatcher dispatcher = dispatcher(
                new InMemoryUserRepository(List.of()));
        UdsResponse response = dispatcher.handle(new UdsRequest("POST",
                "/api/v1" + ApiRoutes.LOGIN, Map.of(),
                "x".repeat(4097)));

        assertEquals(413, response.status());
        assertEquals("PAYLOAD_TOO_LARGE", parse(response).path("code").asText());
    }

    @Test
    void adminCanCreateListUpdateAndDeleteUsers() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository(List.of(
                UserAccount.create("admin", "admin-password", "admin", true)));
        UdsRequestDispatcher dispatcher = dispatcher(users);
        String token = mapper.readTree(dispatcher.handle(new UdsRequest("POST",
                "/api/v1" + ApiRoutes.LOGIN, Map.of(),
                "{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .body()).path("token").asText();

        UdsResponse created = dispatcher.handle(new UdsRequest("POST",
                "/api/v1" + ApiRoutes.USERS,
                Map.of(ApiHeaders.ARBITER_TOKEN, token),
                "{\"username\":\"alice\",\"password\":\"alice-password\","
                        + "\"scope\":\"reader\",\"enabled\":true}"));
        assertEquals(201, created.status());
        assertEquals("alice", mapper.readTree(created.body()).path("username").asText());

        UdsResponse listed = dispatcher.handle(new UdsRequest("GET",
                "/api/v1" + ApiRoutes.USERS,
                Map.of(ApiHeaders.ARBITER_TOKEN, token), null));
        assertEquals(2, mapper.readTree(listed.body()).path("users").size());

        UdsResponse updated = dispatcher.handle(new UdsRequest("PUT",
                "/api/v1" + ApiRoutes.USERS + "/alice",
                Map.of(ApiHeaders.ARBITER_TOKEN, token),
                "{\"scope\":\"admin\",\"enabled\":false}"));
        assertEquals(200, updated.status());
        JsonNode updatedBody = mapper.readTree(updated.body());
        assertEquals("admin", updatedBody.path("scope").asText());
        assertEquals(false, updatedBody.path("enabled").asBoolean());

        UdsResponse deleted = dispatcher.handle(new UdsRequest("DELETE",
                "/api/v1" + ApiRoutes.USERS + "/alice",
                Map.of(ApiHeaders.ARBITER_TOKEN, token), null));
        assertEquals(200, deleted.status());
        assertEquals(404, dispatcher.handle(new UdsRequest("GET",
                "/api/v1" + ApiRoutes.USERS + "/alice",
                Map.of(ApiHeaders.ARBITER_TOKEN, token), null)).status());
    }

    private UdsRequestDispatcher dispatcher(MutableUserRepository users) {
        ServerConfig config = new ServerConfig(
                "127.0.0.1", 0, "/api/v1", Duration.ofHours(1),
                4096, 2, Duration.ofMinutes(1));
        Clock clock = Clock.systemUTC();
        TokenService tokens = new TokenService(SECRET, config.tokenTtl(), clock);
        AuthService auth = new AuthService(users, tokens,
                new LoginRateLimiter(config.loginMaxFailures(),
                        config.loginWindow(), clock));
        return new UdsRequestDispatcher(config, auth, new UserService(users),
                new StatusService(clock), new QueryService());
    }

    private JsonNode parse(UdsResponse response) {
        try {
            return mapper.readTree(response.body());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
