package io.github.arbiter.server.agent.state;

public final class AgentStateException extends RuntimeException {
    public enum Kind {
        CONVERSATION_INVALID,
        CONVERSATION_BUSY,
        RATE_LIMITED,
        BUDGET_EXCEEDED
    }

    private final Kind kind;

    public AgentStateException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
