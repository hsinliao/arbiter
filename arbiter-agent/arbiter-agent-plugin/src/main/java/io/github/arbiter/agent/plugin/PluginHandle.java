package io.github.arbiter.agent.plugin;

import io.github.arbiter.agent.spi.model.PluginDescriptor;

public record PluginHandle(
        PluginDescriptor meta,
        Object instance,
        PluginState state,
        String failure
) {
    public PluginHandle {
        failure = failure == null ? "" : failure;
    }
}
