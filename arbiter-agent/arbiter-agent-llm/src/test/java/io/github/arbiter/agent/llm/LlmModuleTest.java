package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.invocation.RoutingPreference;
import io.github.arbiter.agent.spi.invocation.RoutingRequirements;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                        null, Set.of(), 100, 10, BigDecimal.ZERO,
                        RoutingPreference.LOWEST_COST)));
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
