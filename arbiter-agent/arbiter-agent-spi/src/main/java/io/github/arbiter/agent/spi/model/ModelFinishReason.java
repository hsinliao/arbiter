package io.github.arbiter.agent.spi.model;

public enum ModelFinishReason {
    STOP,
    LENGTH,
    TOOL_CALLS,
    CONTENT_FILTER,
    ERROR,
    UNKNOWN
}
