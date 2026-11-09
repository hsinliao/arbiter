package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.llm.ModelClient;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ScriptedModelClient implements ModelClient {

    private final Deque<ModelResponse> responses = new ArrayDeque<>();
    private final List<ModelRequest> requests = new ArrayList<>();

    public ScriptedModelClient enqueue(ModelResponse response) {
        responses.add(response);
        return this;
    }

    public ScriptedModelClient enqueueText(String content) {
        return enqueue(ModelResponse.builder().content(content).build());
    }

    @Override
    public synchronized ModelResponse chat(ModelRequest request, InvocationContext context) {
        context.checkActive();
        requests.add(request);
        ModelResponse response = responses.poll();
        if (response == null) {
            throw new IllegalStateException("No scripted response remaining");
        }
        return response;
    }

    public synchronized List<ModelRequest> requests() {
        return List.copyOf(requests);
    }
}
