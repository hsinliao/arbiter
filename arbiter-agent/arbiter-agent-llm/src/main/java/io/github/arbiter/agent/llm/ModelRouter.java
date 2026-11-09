package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.invocation.RoutingRequirements;

@FunctionalInterface
public interface ModelRouter {

    ModelRoute select(RoutingRequirements requirements);
}
