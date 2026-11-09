package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.llm.ModelClient;

public record ModelRoute(ModelSpec spec, ModelClient client) {
}
