package io.github.arbiter.client;

import io.github.arbiter.api.ApiHeaders;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.arbiter.client.config.HttpClientConfig;
import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.http.CliHttpClient;
import io.github.arbiter.client.token.StoredToken;
import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.exception.ConflictException;
import io.github.arbiter.core.exception.LoginCredentialsException;
import io.github.arbiter.core.exception.LoginDisabledException;
import io.github.arbiter.core.exception.LoginRateLimitException;
import io.github.arbiter.core.exception.NetworkException;
import io.github.arbiter.core.exception.NotFoundException;
import io.github.arbiter.core.exception.RateLimitException;
import io.github.arbiter.core.exception.ServerException;
import io.github.arbiter.core.service.AuthStatus;
import io.github.arbiter.core.service.LoginResult;
import io.github.arbiter.core.service.SystemStatus;
import io.github.arbiter.core.service.VerificationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestClientTest {
    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void sendsCliTokenAndAcceptsUnknownResponseFields() {
        server.stubFor(get(urlPathEqualTo("/api/v1/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"state":"UP","version":"2.1","serverTime":"now",
                                 "newServerField":{"supported":true}}
                                """)));
        FakeTokenStore tokens = new FakeTokenStore("env-token");
        ApiSystemService service = new ApiSystemService(client(tokens), tokens);

        SystemStatus status = service.status();

        assertEquals("UP", status.state());
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/status"))
                .withHeader(ApiHeaders.ARBITER_TOKEN, equalTo("env-token")));
    }

    @Test
    void sendsQueryParameterInTheQueryComponent() {
        server.stubFor(get(urlPathEqualTo("/api/v1/query"))
                .withQueryParam("name", equalTo("demo.resource"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"demo.resource\",\"value\":\"ok\"}")));
        FakeTokenStore tokens = new FakeTokenStore("token");

        var result = new ApiQueryService(client(tokens), tokens).query("demo.resource");

        assertEquals("demo.resource", result.name());
        assertEquals("ok", result.value());
    }

    @Test
    void mapsEveryRequiredHttpFailure() {
        assertStatusMapping(401, AuthenticationException.class);
        assertStatusMapping(403, io.github.arbiter.core.exception.AuthorizationException.class);
        assertStatusMapping(404, NotFoundException.class);
        assertStatusMapping(409, ConflictException.class);
        assertStatusMapping(429, RateLimitException.class);
        assertStatusMapping(500, ServerException.class);
    }

    @Test
    void doesNotRetryUnauthorizedOrServerErrorsByDefaultForNonIdempotentRequests() {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"AUTH_BACKEND\",\"message\":\"down\"}")));
        FakeTokenStore tokens = new FakeTokenStore();
        ApiAuthService service = new ApiAuthService(client(tokens, 2), tokens, () -> null);

        LoginCredentialsException ignored = null;
        assertThrows(
                io.github.arbiter.core.exception.LoginUnavailableException.class,
                () -> service.login("alice", "password-value"));
        server.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/auth/login")));
    }

    @Test
    void retriesIdempotentGetWithBackoff() {
        server.stubFor(get(urlPathEqualTo("/api/v1/status"))
                .inScenario("eventual-success")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("ready"));
        server.stubFor(get(urlPathEqualTo("/api/v1/status"))
                .inScenario("eventual-success")
                .whenScenarioStateIs("ready")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"state\":\"UP\",\"version\":\"1\",\"serverTime\":\"now\"}")));
        FakeTokenStore tokens = new FakeTokenStore("token");

        SystemStatus status = new ApiSystemService(client(tokens), tokens).status();

        assertEquals("UP", status.state());
        server.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/status")));
    }

    @Test
    void loginPersistsOnlyServerIssuedTokenAndSendsNoCliTokenHeader() {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/login"))
                .withRequestBody(containing("\"username\":\"alice\""))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"token":"issued-token","expiresAt":"2030-01-01",
                                 "username":"alice","scope":"read","extra":"ignored"}
                                """)));
        FakeTokenStore tokens = new FakeTokenStore();
        ApiAuthService service = new ApiAuthService(client(tokens), tokens, () -> null);

        LoginResult result = service.login("alice", "do-not-log");

        assertEquals("alice", result.username());
        assertNotNull(tokens.saved());
        assertEquals("issued-token", tokens.saved().token());
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/auth/login"))
                .withHeader(ApiHeaders.ARBITER_TOKEN, absent()));
    }

    @Test
    void loginDistinguishesCredentialsDisabledRateLimitAndNetwork() {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"BAD_CREDENTIALS\",\"message\":\"wrong\"}")));
        FakeTokenStore tokens = new FakeTokenStore();
        ApiAuthService service = new ApiAuthService(client(tokens), tokens, () -> null);
        assertThrows(LoginCredentialsException.class,
                () -> service.login("alice", "wrong-password"));

        server.resetAll();
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse().withStatus(403)));
        assertThrows(LoginDisabledException.class,
                () -> service.login("alice", "password"));

        server.resetAll();
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse().withStatus(429)));
        assertThrows(LoginRateLimitException.class,
                () -> service.login("alice", "password"));

        server.stop();
        assertThrows(io.github.arbiter.core.exception.LoginUnavailableException.class,
                () -> service.login("alice", "password"));
        assertTrue(tokens.saved() == null);
    }

    @Test
    void authStatusDistinguishesValidInvalidAndUnverifiable() {
        FakeTokenStore validTokens = new FakeTokenStore("secret-token");
        server.stubFor(get(urlPathEqualTo("/api/v1/auth/whoami"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"subject":"alice","expiresAt":"2030-01-01",
                                 "unknownField":"ignored"}
                                """)));
        AuthStatus valid = new ApiAuthService(client(validTokens), validTokens, () -> null)
                .status();
        assertEquals(VerificationResult.State.VALID, valid.verification().state());
        assertTrue(valid.tokenHint().startsWith("sha256:"));
        assertFalse(valid.tokenHint().contains("secret-token"));

        server.resetAll();
        server.stubFor(get(urlPathEqualTo("/api/v1/auth/whoami"))
                .willReturn(aResponse().withStatus(401)));
        FakeTokenStore invalidTokens = new FakeTokenStore("secret-token");
        AuthStatus invalid = new ApiAuthService(client(invalidTokens), invalidTokens,
                () -> null).status();
        assertEquals(VerificationResult.State.INVALID, invalid.verification().state());
        server.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/auth/whoami")));

        server.resetAll();
        server.stubFor(get(urlPathEqualTo("/api/v1/auth/whoami"))
                .willReturn(aResponse().withStatus(503)));
        FakeTokenStore unreachableTokens = new FakeTokenStore("secret-token");
        AuthStatus unavailable = new ApiAuthService(client(unreachableTokens),
                unreachableTokens, () -> null).status();
        assertEquals(VerificationResult.State.UNVERIFIABLE,
                unavailable.verification().state());
    }

    @Test
    void verboseLogsNeverContainTokenOrPassword() {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"BAD\",\"message\":\"do-not-log\"}")));
        List<String> logs = new ArrayList<>();
        FakeTokenStore tokens = new FakeTokenStore();
        AtomicBoolean verbose = new AtomicBoolean(true);
        ApiClient client = new CliHttpClient(config(2),
                verbose::get, RestClientTest::formatLog, logs::add);
        ApiAuthService service = new ApiAuthService(client, tokens, () -> null);

        assertThrows(LoginCredentialsException.class,
                () -> service.login("alice", "do-not-log"));

        String allLogs = String.join("\n", logs);
        assertFalse(allLogs.contains("do-not-log"));
        assertTrue(allLogs.contains("\"password\":\"***\""));

        FakeTokenStore tokenStore = new FakeTokenStore("raw-token-value");
        server.resetAll();
        server.stubFor(get(urlPathEqualTo("/api/v1/status"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"state\":\"UP\"}")));
        ApiSystemService statusService = new ApiSystemService(
                new CliHttpClient(config(2), verbose::get,
                        RestClientTest::formatLog, logs::add),
                tokenStore);
        statusService.status();
        assertTrue(String.join("\n", logs).contains("ARBITER_TOKEN: ***"));
        assertFalse(String.join("\n", logs).contains("raw-token-value"));
    }

    @Test
    void readTimeoutMapsToNetworkFailure() {
        server.stubFor(get(urlPathEqualTo("/api/v1/status"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(300)
                        .withBody("{}")));
        FakeTokenStore tokens = new FakeTokenStore("token");
        HttpClientConfig base = config(0);
        HttpClientConfig fast = new HttpClientConfig(base.baseUri(), base.apiPrefix(),
                base.connectTimeout(), Duration.ofMillis(50), base.poolAcquireTimeout(),
                base.poolPermits(), 0, base.retryBaseDelay());
        ApiClient fastClient = new CliHttpClient(fast, () -> false,
                (key, args) -> key, ignored -> {
                });

        assertThrows(NetworkException.class,
                () -> new ApiSystemService(fastClient, tokens).status());
    }

    private void assertStatusMapping(int status, Class<? extends Throwable> expected) {
        server.resetAll();
        server.stubFor(get(urlPathEqualTo("/api/v1/status"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"CODE_" + status
                                + "\",\"message\":\"server text\"}")));
        FakeTokenStore tokens = new FakeTokenStore("token");
        ApiSystemService service = new ApiSystemService(client(tokens, 0), tokens);

        RuntimeException error = assertThrows(RuntimeException.class, service::status);
        assertTrue(expected.isInstance(error), () -> "Expected " + expected
                + " but got " + error.getClass());
        assertTrue(error.getMessage().contains(error instanceof
                io.github.arbiter.core.exception.DomainException domain
                ? domain.messageKey() : ""));
    }

    private static String formatLog(String key, Object[] args) {
        if ("verbose.http.token".equals(key)) {
            return "ARBITER_TOKEN: ***";
        }
        if ("verbose.http.body".equals(key)) {
            return "Request body: " + (args.length == 0 ? "" : args[0]);
        }
        return key;
    }

    private ApiClient client(FakeTokenStore tokens) {
        return client(tokens, 2);
    }

    private ApiClient client(FakeTokenStore tokens, int retries) {
        return new CliHttpClient(config(retries), () -> false,
                (key, args) -> key, ignored -> {
                });
    }

    private HttpClientConfig config(int retries) {
        return new HttpClientConfig(
                URI.create("http://localhost:" + server.port()),
                "/api/v1",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                2,
                retries,
                Duration.ofMillis(10)
        );
    }
}
