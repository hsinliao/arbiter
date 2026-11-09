package io.github.arbiter.agent.spi.llm;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;

@FunctionalInterface
public interface ModelClient {

    ModelResponse chat(ModelRequest request, InvocationContext context);
}
