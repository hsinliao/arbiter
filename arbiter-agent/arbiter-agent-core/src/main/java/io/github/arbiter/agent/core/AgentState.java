package io.github.arbiter.agent.core;

public enum AgentState {
    CREATED,
    RUNNING,
    WAITING_FOR_MODEL,
    EXECUTING_TOOL,
    COMPLETED,
    FAILED,
    CANCELLED
}
