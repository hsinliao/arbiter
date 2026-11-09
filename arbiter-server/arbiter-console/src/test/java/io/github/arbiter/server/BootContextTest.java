package io.github.arbiter.server;

import tools.jackson.databind.ObjectMapper;

import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.api.config.ArbiterKeys;
import io.github.arbiter.api.uds.UdsRequest;
import io.github.arbiter.api.uds.UdsResponse;
import io.github.arbiter.server.security.ApiResponseHeaderFilter;
import io.github.arbiter.server.security.ManagementMetricsSecurityFilter;
import io.github.arbiter.server.security.RequestSizeLimitFilter;
import io.github.arbiter.server.uds.UdsRequestDispatcher;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ArbiterServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "arbiter.server.host=127.0.0.1",
                "arbiter.server.port=0",
                "arbiter.api.prefix=/api/v1",
                "arbiter.transport=http",
                "arbiter.server.max.request.bytes=1024",
                "management.endpoint.health.show-details=always",
                "management.endpoints.web.exposure.include=health,info,metrics"
        })
class BootContextTest {
    private static final Path HOME = createHome();

    @DynamicPropertySource
    static void arbiterHome(DynamicPropertyRegistry registry) {
        System.setProperty(ArbiterKeys.SystemProperty.HOME, HOME.toString());
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestSizeLimitFilter requestSizeLimitFilter;

    @Autowired
    private ApiResponseHeaderFilter apiResponseHeaderFilter;

    @Autowired
    private ManagementMetricsSecurityFilter managementMetricsSecurityFilter;

    @Autowired
    private UdsRequestDispatcher uds;

    private MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestSizeLimitFilter, apiResponseHeaderFilter,
                        managementMetricsSecurityFilter)
                .build();
    }

    @AfterAll
    static void clearHome() {
        System.clearProperty(ArbiterKeys.SystemProperty.HOME);
    }

    @Test
    void usesTheRealPrefixFiltersInterceptorAndActuator() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mvc.perform(request(HttpMethod.GET, "/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers
                        .jsonPath("$.components.userStore.status").value("UP"))
                .andExpect(MockMvcResultMatchers
                        .jsonPath("$.components.securityMaterial.status").value("UP"));

        mvc.perform(request(HttpMethod.GET, "/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        String token = login();
        mvc.perform(request(HttpMethod.GET, "/actuator/metrics")
                        .header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isOk());

        mvc.perform(request(HttpMethod.GET, "/api/v1/status")
                        .header(ApiHeaders.ARBITER_TOKEN, token))
                .andExpect(status().isOk());

        mvc.perform(request(HttpMethod.GET, "/api/v1/nope"))
                .andExpect(status().isNotFound());

        mvc.perform(request(HttpMethod.GET, "/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(request(HttpMethod.POST, "/api/v1/auth/login")
                        .contentType("application/json")
                        .content("x".repeat(1025)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void servesConsolePagesAndConfig() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/login.html"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers
                .content().string(Matchers.containsString(
                                "Arbiter Console")));
        mvc.perform(request(HttpMethod.GET, "/console/config"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers
                        .jsonPath("$.apiPrefix").value("/api/v1"));
        mvc.perform(request(HttpMethod.GET, "/"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void httpAndUdsAgreeOnAuthenticatedRoutesAndBodyLimit() throws Exception {
        String token = login();
        Map<String, String> auth = Map.of(ApiHeaders.ARBITER_TOKEN, token);

        assertParity("GET", "/api/v1/status", auth, null, 200, null);
        assertParity("GET", "/api/v1/auth/whoami", auth, null, 200, null);
        assertParity("GET", "/api/v1/query?name=demo", auth, null, 200, null);
        assertParity("GET", "/api/v1/users", auth, null, 200, null);
        assertParity("GET", "/api/v1/query", auth, null, 400, "INVALID_ARGUMENT");
        assertParity("POST", "/api/v1/auth/login", Map.of(),
                "x".repeat(1025), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void lifecycleThreadsUseStableNames() {
        assertTrue(hasThreadNamed("arbiter-stop-watcher"));
        assertTrue(hasThreadNamed("arbiter-shutdown"));
        assertFalse(hasThreadNamed("arbiter-server-stop-controller"));
    }

    @ParameterizedTest
    @MethodSource("contractCases")
    void httpAndUdsReturnTheSameStatusAndCode(ContractCase testCase) throws Exception {
        MvcResult http = mvc.perform(request(HttpMethod.valueOf(testCase.method()),
                        testCase.path())
                        .contentType("application/json")
                        .content(testCase.body() == null ? "" : testCase.body()))
                .andReturn();
        UdsResponse udsResponse = uds.handle(new UdsRequest(testCase.method(),
                testCase.path(), Map.of(), testCase.body()));

        assertEquals(testCase.status(), http.getResponse().getStatus(),
                testCase.name());
        assertEquals(testCase.status(), udsResponse.status(), testCase.name());
        assertEquals(testCase.code(), code(new String(
                http.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)),
                testCase.name());
        assertEquals(testCase.code(), code(udsResponse.body()), testCase.name());
    }

    static Stream<ContractCase> contractCases() {
        return Stream.of(
                new ContractCase("status without token", "GET", "/api/v1/status",
                        null, 401, "AUTH_REQUIRED"),
                new ContractCase("unknown path", "GET", "/api/v1/nope",
                        null, 404, "NOT_FOUND"),
                new ContractCase("wrong method", "GET", "/api/v1/auth/login",
                        null, 405, "METHOD_NOT_ALLOWED"),
                new ContractCase("wrong password", "POST", "/api/v1/auth/login",
                        "{\"username\":\"admin\",\"password\":\"wrong\"}",
                        401, "BAD_CREDENTIALS"));
    }

    private void assertParity(String method, String path, Map<String, String> headers,
                              String body, int status, String code) throws Exception {
        var requestBuilder = request(HttpMethod.valueOf(method), path)
                .contentType("application/json")
                .content(body == null ? "" : body);
        headers.forEach(requestBuilder::header);
        MvcResult http = mvc.perform(requestBuilder).andReturn();
        UdsResponse udsResponse = uds.handle(new UdsRequest(method, path, headers, body));

        assertEquals(status, http.getResponse().getStatus(), path);
        assertEquals(status, udsResponse.status(), path);
        assertEquals(code, code(new String(http.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8)), path);
        assertEquals(code, code(udsResponse.body()), path);
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(request(HttpMethod.POST,
                        "/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\""
                                + readPassword() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = new ObjectMapper().readTree(
                result.getResponse().getContentAsString()).path("token").asText();
        assertNotNull(token);
        return token;
    }

    private static String code(String body) {
        try {
            var node = new ObjectMapper().readTree(body);
            return node.has("code") ? node.path("code").asText() : null;
        } catch (Exception failure) {
            return null;
        }
    }

    private static Path createHome() {
        try {
            return Files.createTempDirectory("arbiter-boot-test");
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static String readPassword() {
        try {
            String content = Files.readString(
                    HOME.resolve("conf/security/initial-admin.txt"),
                    StandardCharsets.UTF_8);
            for (String line : content.split("\\R")) {
                if (line.startsWith("password=")) {
                    return line.substring("password=".length());
                }
            }
            throw new IllegalStateException("initial password missing");
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static boolean hasThreadNamed(String name) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals(name));
    }

    private record ContractCase(String name, String method, String path,
                                String body, int status, String code) {
    }
}
