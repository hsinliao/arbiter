package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.llm.ModelClient;
import io.github.arbiter.agent.spi.model.MessageRole;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StructuredOutputModelClient implements ModelClient {

    private final ModelClient delegate;
    private final String schemaDescription;

    public StructuredOutputModelClient(ModelClient delegate, String schemaDescription) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.schemaDescription = schemaDescription == null ? "" : schemaDescription;
    }

    @Override
    public ModelResponse chat(ModelRequest request, InvocationContext context) {
        List<ModelMessage> messages = new ArrayList<>(request.messages());
        String instruction = "Return exactly one JSON value with no markdown. "
                + "Do not wrap it in code fences. " + schemaDescription;
        if (messages.isEmpty() || messages.get(0).role() != MessageRole.SYSTEM) {
            messages.add(0, ModelMessage.system(instruction));
        } else {
            ModelMessage original = messages.get(0);
            messages.set(0, ModelMessage.system(original.content() + "\n" + instruction));
        }
        ModelRequest structuredRequest = new ModelRequest(
                request.modelId(), messages, request.sampling(), request.tools(), request.metadata());
        ModelResponse response = delegate.chat(structuredRequest, context);
        return new ModelResponse(
                StructuredOutput.extractJson(response.content()),
                response.modelId(),
                response.toolCalls(),
                response.usage(),
                response.finishReason(),
                response.providerMetadata());
    }
}
