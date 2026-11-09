package io.github.arbiter.agent.spi;

import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.error.ProviderErrorKind;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.memory.Embedding;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpiContractTest {

    @Test
    void modelAndToolValuesAreDefensivelyImmutable() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("value", 1);
        ToolCall call = new ToolCall("call-1", "demo", arguments);
        arguments.put("value", 2);
        assertEquals(1, call.arguments().get("value"));
        assertThrows(UnsupportedOperationException.class, () -> call.arguments().put("x", 3));

        List<ModelMessage> messages = new ArrayList<>(List.of(ModelMessage.user("hello")));
        ModelRequest request = new ModelRequest(
                null, messages, null, List.of(ToolDefinition.builder()
                .name("demo").description("A deterministic demo tool.").build()), Map.of());
        messages.clear();
        assertEquals(1, request.messages().size());
    }

    @Test
    void invocationContextSharesCancellationAndDeadlineSemantics() {
        AtomicBoolean cancelled = new AtomicBoolean();
        InvocationContext context = new InvocationContext(
                null, "trace", "task", "session",
                Instant.now().plusSeconds(30), cancelled::get, null, Map.of());
        context.checkActive();
        cancelled.set(true);

        ProviderException error = assertThrows(ProviderException.class, context::checkActive);
        assertEquals(ProviderErrorKind.CANCELLED, error.kind());
    }

    @Test
    void embeddingIsAnActualValueObject() {
        Embedding first = new Embedding(new double[]{1.0, 2.0});
        Embedding second = new Embedding(new double[]{1.0, 2.0});

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertArrayEquals(new double[]{1.0, 2.0}, first.values());
    }
}
