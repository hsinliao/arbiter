package io.github.arbiter.agent.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.error.ProviderErrorKind;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.invocation.RoutingPreference;
import io.github.arbiter.agent.spi.invocation.RoutingRequirements;
import io.github.arbiter.agent.spi.llm.ModelStreamHandle;
import io.github.arbiter.agent.spi.llm.ModelStreamObserver;
import io.github.arbiter.agent.spi.model.ModelFinishReason;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.ToolCallDelta;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmModuleTest {

    @Test
    void routesToCheapestEligibleModel() {
        CostAwareModelRouter router = new CostAwareModelRouter();
        ScriptedModelClient cheap = new ScriptedModelClient().enqueueText("cheap");
        ScriptedModelClient premium = new ScriptedModelClient().enqueueText("premium");
        router.register(ModelSpec.builder("cheap")
                .inputCost(BigDecimal.ONE).outputCost(BigDecimal.ONE).build(), cheap);
        router.register(ModelSpec.builder("premium")
                .inputCost(BigDecimal.TEN).outputCost(BigDecimal.TEN).build(), premium);

        RoutingModelClient client = new RoutingModelClient(router);
        ModelResponse response = client.chat(
                request("hello"),
                context(new RoutingRequirements(
                        null, Set.of(), 0, 0, null, RoutingPreference.LOWEST_COST)));

        assertEquals("cheap", response.content());
        assertEquals("cheap", response.modelId());
    }

    @Test
    void extractsJsonFromMarkdownFence() {
        String json = StructuredOutput.extractJson("""
                Here is the result:
                ```json
                {"answer": {"value": 42}}
                ```
                """);
        assertEquals("{\"answer\": {\"value\": 42}}", json);
        assertEquals("{\"ok\": true}",
                StructuredOutput.extractJson("Use [this] label, then {\"ok\": true}."));
    }

    @Test
    void rejectsUnavailablePreferredModelWhenNothingElseFits() {
        CostAwareModelRouter router = new CostAwareModelRouter();
        router.register(ModelSpec.builder("offline").available(false).build(),
                new ScriptedModelClient());

        assertThrows(IllegalStateException.class, () -> router.select(
                new RoutingRequirements(
                        "offline", Set.of(), 0, 0, null, RoutingPreference.BALANCED)));
    }

    @Test
    void distinguishesZeroBudgetFromUnlimitedRouting() {
        CostAwareModelRouter router = new CostAwareModelRouter();
        router.register(ModelSpec.builder("paid")
                .inputCost(BigDecimal.ONE)
                .outputCost(BigDecimal.ONE)
                .build(), new ScriptedModelClient().enqueueText("paid"));

        assertThrows(IllegalStateException.class, () -> router.select(
                new RoutingRequirements(
                        null, Set.of(), 100, 10, BigDecimal.ZERO, RoutingPreference.LOWEST_COST)));
        assertEquals("paid", router.select(
                new RoutingRequirements(
                        null, Set.of(), 100, 10, null, RoutingPreference.LOWEST_COST))
                .spec().code());
    }

    @Test
    void excludesModelWhoseContextWindowIsTooSmall() {
        CostAwareModelRouter router = new CostAwareModelRouter();
        router.register(ModelSpec.builder("small")
                .maxContextTokens(20)
                .build(), new ScriptedModelClient());
        router.register(ModelSpec.builder("large")
                .maxContextTokens(1_000)
                .build(), new ScriptedModelClient());

        assertEquals("large", router.select(
                new RoutingRequirements(
                        null, Set.of(), 100, 50, null, RoutingPreference.PRIORITY))
                .spec().code());
        assertThrows(IllegalStateException.class, () -> router.select(
                new RoutingRequirements(
                        null, Set.of(), Integer.MAX_VALUE, Integer.MAX_VALUE, null,
                        RoutingPreference.PRIORITY)));
    }

    @Test
    void langChainAdapterMapsToolsUsageAndFinishReason() {
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        LangChainModelClient client = new LangChainModelClient(new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                captured.set(request);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(
                                ToolExecutionRequest.builder()
                                        .id("call-1")
                                        .name("lookup")
                                        .arguments("{\"id\":7}")
                                        .build()))
                        .modelName("fake")
                        .tokenUsage(new TokenUsage(2, 3))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
        });

        ModelResponse response = client.chat(
                ModelRequest.builder()
                        .messages(List.of(ModelMessage.user("find 7")))
                        .tools(List.of(ToolDefinition.builder()
                                .name("lookup")
                                .description("Looks up a record by its numeric identifier.")
                                .parameters(Map.of(
                                        "type", "object",
                                        "properties", Map.of("id", Map.of("type", "string"))))
                                .build()))
                        .build(),
                context(RoutingRequirements.unlimited(null)));

        assertEquals("fake", response.modelId());
        assertEquals("call-1", response.toolCalls().get(0).id());
        assertEquals(7, response.toolCalls().get(0).arguments().get("id"));
        assertEquals(3, response.usage().outputTokens());
        assertEquals(ModelFinishReason.TOOL_CALLS, response.finishReason());
        assertTrue(captured.get().toolSpecifications().get(0)
                .parameters().properties().get("id") instanceof JsonStringSchema);
    }

    @Test
    void langChainStreamingAdapterEmitsContentAndTerminalResponse() {
        LangChainStreamingModelClient client = new LangChainStreamingModelClient(
                new StreamingChatModel() {
                    @Override
                    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                        handler.onPartialResponse("hel");
                        handler.onPartialResponse("lo");
                        handler.onCompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from("hello"))
                                .finishReason(FinishReason.STOP)
                                .build());
                    }
                });
        StringBuilder content = new StringBuilder();
        AtomicReference<ModelResponse> terminal = new AtomicReference<>();
        AtomicReference<ProviderException> error = new AtomicReference<>();

        try (ModelStreamHandle ignored = client.stream(
                request("hello"),
                context(RoutingRequirements.unlimited(null)),
                new ModelStreamObserver() {
                    @Override
                    public void onContent(String delta) {
                        content.append(delta);
                    }

                    @Override
                    public void onToolCall(ToolCallDelta delta) {
                        throw new AssertionError("Unexpected tool delta");
                    }

                    @Override
                    public void onComplete(ModelResponse response) {
                        terminal.set(response);
                    }

                    @Override
                    public void onError(ProviderException providerError) {
                        error.set(providerError);
                    }
                })) {
            // The fake model completes synchronously.
        }

        assertEquals("hello", content.toString());
        assertEquals(ModelFinishReason.STOP, terminal.get().finishReason());
        assertNull(error.get());
    }

    @Test
    void mapsProviderErrorsIntoStandardKinds() {
        ProviderException error = LangChainErrors.translate(
                new IllegalStateException("401 unauthorized"));

        assertEquals(ProviderErrorKind.AUTHENTICATION, error.kind());
        assertFalse(error.retryable());
    }

    private static ModelRequest request(String content) {
        return ModelRequest.builder()
                .messages(List.of(ModelMessage.user(content)))
                .build();
    }

    private static InvocationContext context(RoutingRequirements routing) {
        return new InvocationContext(
                null, "trace", "task", "session",
                Instant.now().plus(Duration.ofMinutes(1)),
                () -> false, routing, Map.of());
    }
}
