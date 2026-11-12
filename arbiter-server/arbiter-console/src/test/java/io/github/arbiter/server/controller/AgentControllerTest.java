package io.github.arbiter.server.controller;

import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.api.dto.AgentCapabilitiesResponse;
import io.github.arbiter.api.dto.AgentRunRequest;
import io.github.arbiter.api.dto.AgentRunResponse;
import io.github.arbiter.server.auth.InMemoryUserRepository;
import io.github.arbiter.server.auth.LoginRateLimiter;
import io.github.arbiter.server.auth.TokenService;
import io.github.arbiter.server.auth.UserAccount;
import io.github.arbiter.server.config.ServerConfig;
import io.github.arbiter.server.error.ApiExceptionHandler;
import io.github.arbiter.server.security.TokenAuthenticationInterceptor;
import io.github.arbiter.server.service.AgentAssistant;
import io.github.arbiter.server.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> submittedOwner = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig(
                "127.0.0.1", 0, "/api/v1", Duration.ofHours(1),
                4096, 2, Duration.ofMinutes(1));
        Clock clock = Clock.systemUTC();
        InMemoryUserRepository users = new InMemoryUserRepository(List.of(
                UserAccount.create("reader", "reader-password", "reader", true)));
        TokenService tokens = new TokenService(
                "0123456789abcdef0123456789abcdef", config.tokenTtl(), clock);
        AuthService auth = new AuthService(users, tokens,
                new LoginRateLimiter(config.loginMaxFailures(),
                        config.loginWindow(), clock));
        AgentAssistant assistant = new FakeAssistant(submittedOwner);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(auth),
                        new AgentController(assistant))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new TokenAuthenticationInterceptor(auth))
                .build();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/agent/capabilities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitsWithAuthenticatedOwnerAndReturnsAccepted() throws Exception {
        String token = login();

        mvc.perform(post("/agent/runs")
                        .header(ApiHeaders.ARBITER_TOKEN, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"status?\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.conversationId").value("conversation-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        assertEquals("reader", submittedOwner.get());
    }

    @Test
    void exposesCapabilitiesToReader() throws Exception {
        String token = login();

        mvc.perform(get("/agent/capabilities")
                        .header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mode").value("read-only"));
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reader\","
                                + "\"password\":\"reader-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
                .path("token").asText();
    }

    private static final class FakeAssistant implements AgentAssistant {
        private final AtomicReference<String> submittedOwner;

        private FakeAssistant(AtomicReference<String> submittedOwner) {
            this.submittedOwner = submittedOwner;
        }

        @Override
        public AgentCapabilitiesResponse capabilities() {
            return new AgentCapabilitiesResponse(
                    true, "test-model", "read-only", "ready");
        }

        @Override
        public AgentRunResponse submit(
                TokenService.TokenClaims claims, AgentRunRequest request) {
            submittedOwner.set(claims.subject());
            return response("RUNNING");
        }

        @Override
        public AgentRunResponse get(
                TokenService.TokenClaims claims, String runId) {
            return response("SUCCEEDED");
        }

        private static AgentRunResponse response(String status) {
            return new AgentRunResponse(
                    "run-1", "conversation-1", status, "", "",
                    "2026-11-12T00:00:00Z", null, 0, 0, 0,
                    BigDecimal.ZERO, List.of());
        }
    }
}
