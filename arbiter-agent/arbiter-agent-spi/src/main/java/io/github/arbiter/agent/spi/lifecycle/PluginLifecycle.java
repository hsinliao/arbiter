package io.github.arbiter.agent.spi.lifecycle;

import io.github.arbiter.agent.spi.model.PluginConfig;

public interface PluginLifecycle extends AutoCloseable {

    void start(PluginConfig config);

    void stop();

    @Override
    default void close() {
        stop();
    }
}
