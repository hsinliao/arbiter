package io.github.arbiter.agent.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentEvent(
        String eventId,
        String traceId,
        String spanId,
        String taskId,
        String sessionId,
        long sequence,
        AgentEventType type,
        Instant occurredAt,
        Map<String, Object> attributes
) {
    public AgentEvent {
        eventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        traceId = traceId == null ? "" : traceId;
        spanId = spanId == null ? "" : spanId;
        taskId = taskId == null ? "" : taskId;
        sessionId = sessionId == null ? "" : sessionId;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static Builder builder(AgentEventType type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final AgentEventType type;
        private String eventId;
        private String traceId;
        private String spanId;
        private String taskId;
        private String sessionId;
        private long sequence;
        private Instant occurredAt = Instant.now();
        private Map<String, Object> attributes = Map.of();

        private Builder(AgentEventType type) {
            this.type = type;
        }

        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder spanId(String spanId) { this.spanId = spanId; return this; }
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder sequence(long sequence) { this.sequence = sequence; return this; }
        public Builder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder attributes(Map<String, Object> attributes) { this.attributes = attributes; return this; }

        public AgentEvent build() {
            return new AgentEvent(eventId, traceId, spanId, taskId, sessionId, sequence, type, occurredAt, attributes);
        }
    }
}
