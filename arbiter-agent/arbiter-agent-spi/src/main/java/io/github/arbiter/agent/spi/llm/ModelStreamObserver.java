package io.github.arbiter.agent.spi.llm;

import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.ToolCallDelta;

/**
 * Exactly one of onComplete or onError must be invoked as the terminal callback.
 */
public interface ModelStreamObserver {

    void onContent(String delta);

    void onToolCall(ToolCallDelta delta);

    void onComplete(ModelResponse response);

    void onError(ProviderException error);
}
