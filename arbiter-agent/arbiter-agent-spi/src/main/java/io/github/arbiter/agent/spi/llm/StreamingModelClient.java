package io.github.arbiter.agent.spi.llm;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.model.ModelRequest;

public interface StreamingModelClient extends ModelClient {

    ModelStreamHandle stream(
            ModelRequest request,
            InvocationContext context,
            ModelStreamObserver observer
    );
}
