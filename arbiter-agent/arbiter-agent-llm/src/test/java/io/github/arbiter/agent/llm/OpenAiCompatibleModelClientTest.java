package io.github.arbiter.agent.llm;

import com.sun.net.httpserver.HttpServer;
import io.github.arbiter.agent.spi.error.ProviderErrorKind;
import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.model.ModelFinishReason;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.SamplingOptions;
import io.github.arbiter.agent.spi.model.ToolDefinition;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleModelClientTest {

    @Test
    void sendsChatCompletionsAndParsesToolCallsWithJacksonThree()
            throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(200, """
                {
                  "model": "test-model-2026",
                  "choices": [{
                    "message": {
                      "content": "Checking the clock.",
                      "tool_calls": [{
                        "id": "call-1",
                        "type": "function",
                        "function": {
                          "name": "current_time",
                          "arguments": "{\\"timezone\\":\\"Asia/Shanghai\\"}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 8,
                    "total_tokens": 20
                  }
                }
                """, authorization, requestBody);
        server.start();
        try {
            OpenAiCompatibleModelClient client = client(server);
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(
                            ModelMessage.system("Be helpful."),
                            ModelMessage.user("What time is it?")))
                    .sampling(new SamplingOptions(0.2, null, 100))
                    .tools(List.of(ToolDefinition.builder()
                            .name("current_time")
                            .description("Returns time")
                            .parameters(Map.of("type", "object"))
                            .build()))
                    .build();

            ModelResponse response = client.chat(
                    request, InvocationContext.of("task", "session", null));

            assertEquals("Bearer test-secret", authorization.get());
            assertTrue(requestBody.get().contains("\"model\":\"test-model\""));
            assertTrue(requestBody.get().contains("\"tools\""));
            assertEquals("Checking the clock.", response.content());
            assertEquals("test-model-2026", response.modelId());
            assertEquals("current_time", response.toolCalls().get(0).name());
            assertEquals("Asia/Shanghai",
                    response.toolCalls().get(0).arguments().get("timezone"));
            assertEquals(20, response.usage().totalTokens());
            assertEquals(ModelFinishReason.TOOL_CALLS, response.finishReason());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsUnauthorizedResponsesWithoutRetrying() throws Exception {
        HttpServer server = server(401, "{\"error\":\"invalid\"}",
                new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            OpenAiCompatibleModelClient client = client(server);
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(ModelMessage.user("hello")))
                    .build();

            ProviderException failure = assertThrows(ProviderException.class,
                    () -> client.chat(
                            request, InvocationContext.of("task", "session", null)));
            assertEquals(ProviderErrorKind.AUTHENTICATION, failure.kind());
            assertFalse(failure.retryable());
        } finally {
            server.stop(0);
        }
    }

    private static OpenAiCompatibleModelClient client(HttpServer server) {
        return new OpenAiCompatibleModelClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-secret",
                "test-model",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
    }

    private static HttpServer server(
            int status,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody
    ) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders()
                    .getFirst("Authorization"));
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }
}
