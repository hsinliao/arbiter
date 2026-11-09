package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.llm.ModelClient;
import io.github.arbiter.agent.spi.llm.ModelStreamHandle;
import io.github.arbiter.agent.spi.llm.ModelStreamObserver;
import io.github.arbiter.agent.spi.llm.StreamingModelClient;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;

import java.util.Objects;

public final class RoutingModelClient implements StreamingModelClient {

    private final ModelRouter router;

    public RoutingModelClient(ModelRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public ModelResponse chat(ModelRequest request, InvocationContext context) {
        ModelRoute route = select(context);
        ModelRequest routed = withModel(request, route.spec().code());
        ModelResponse response = route.client().chat(routed, context);
        return response.modelId() == null || response.modelId().isBlank()
                ? response.withModelId(route.spec().code()) : response;
    }

    @Override
    public ModelStreamHandle stream(
            ModelRequest request,
            InvocationContext context,
            ModelStreamObserver observer
    ) {
        ModelRoute route = select(context);
        if (!(route.client() instanceof StreamingModelClient streamingClient)) {
            throw ProviderException.invalidRequest(
                    "Selected model does not support streaming: " + route.spec().code());
        }
        return streamingClient.stream(withModel(request, route.spec().code()), context, observer);
    }

    private static ModelRequest withModel(ModelRequest request, String modelId) {
        return new ModelRequest(
                modelId, request.messages(), request.sampling(), request.tools(), request.metadata());
    }

    private ModelRoute select(InvocationContext context) {
        try {
            return router.select(context.routing());
        } catch (RuntimeException error) {
            throw ProviderException.invalidRequest(error.getMessage());
        }
    }
}
