package io.github.arbiter.agent.spi.lifecycle;

public interface ProviderLifecycle extends AutoCloseable {

    default void start(ProviderConfig config) {
    }

    default ProviderHealth health() {
        return ProviderHealth.up();
    }

    default void stop() {
    }

    @Override
    default void close() {
        stop();
    }
}
