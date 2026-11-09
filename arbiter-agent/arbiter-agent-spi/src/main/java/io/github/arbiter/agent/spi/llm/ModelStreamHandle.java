package io.github.arbiter.agent.spi.llm;

public interface ModelStreamHandle extends AutoCloseable {

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
