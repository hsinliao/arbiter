package io.github.arbiter.server.controller;

import tools.jackson.databind.ObjectMapper;

import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.server.auth.InMemoryUserRepository;
import io.github.arbiter.server.auth.LoginRateLimiter;
import io.github.arbiter.server.auth.MutableUserRepository;
import io.github.arbiter.server.auth.TokenService;
import io.github.arbiter.server.auth.UserAccount;
import io.github.arbiter.server.config.ServerConfig;
import io.github.arbiter.server.error.ApiExceptionHandler;
import io.github.arbiter.server.security.TokenAuthenticationInterceptor;
import io.github.arbiter.server.service.AuthService;
import io.github.arbiter.server.service.QueryService;
import io.github.arbiter.server.service.StatusService;
import io.github.arbiter.server.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig(
                "127.0.0.1", 0, "/api/v1", Duration.ofHours(1),
                4096, 2, Duration.ofMinutes(1));
        Clock clock = Clock.systemUTC();
        MutableUserRepository users = new InMemoryUserRepository(List.of(
                UserAccount.create("admin", "admin-password", "admin", true),
                UserAccount.create("reader", "reader-password", "reader", true)));
        TokenService tokens = new TokenService(SECRET, config.tokenTtl(), clock);
        AuthService auth = new AuthService(users, tokens,
                new LoginRateLimiter(config.loginMaxFailures(),
                        config.loginWindow(), clock));
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(auth),
                        new StatusController(new StatusService(clock)),
                        new QueryController(new QueryService()),
                        new UserController(new UserService(users)),
                        new UnannotatedController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new TokenAuthenticationInterceptor(auth))
                .build();
    }

    @Test
    void loginAndReadStatusThroughControllers() throws Exception {
        String token = login("admin", "admin-password", "admin");

        mvc.perform(get("/status").header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UP"));
    }

    @Test
    void protectedEndpointRequiresAToken() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void adminCanListUsers() throws Exception {
        String token = login("admin", "admin-password", "admin");

        mvc.perform(get("/users").header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2));
    }

    @Test
    void unannotatedEndpointRequiresAToken() throws Exception {
        mvc.perform(get("/unannotated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        String token = login("admin", "admin-password", "admin");
        mvc.perform(get("/unannotated").header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isOk());
    }

    @Test
    void readerCannotListUsers() throws Exception {
        String token = login("reader", "reader-password", "reader");

        mvc.perform(get("/users").header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }

    @RestController
    static final class UnannotatedController {
    @GetMapping("/unannotated")
        String value() {
            return "ok";
        }
    }

    private String login(String username, String password, String expectedScope)
            throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\""
                                + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value(expectedScope))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
                .path("token").asText();
    }
}
