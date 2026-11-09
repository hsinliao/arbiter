package io.github.arbiter.agent.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentContext {

    private final String taskId;
    private final String sessionId;
    private final Instant deadline;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ConcurrentHashMap<String, Object> variables = new ConcurrentHashMap<>();

    public AgentContext(String taskId, String sessionId, Instant deadline, Map<String, Object> variables) {
        this.taskId = taskId;
        this.sessionId = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        this.deadline = deadline;
        if (variables != null) {
            this.variables.putAll(variables);
        }
    }

    public static AgentContext create(String taskId) {
        return new AgentContext(taskId, UUID.randomUUID().toString(), null, Map.of());
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Map<String, Object> getVariables() {
        return Map.copyOf(variables);
    }

    public Object get(String key) {
        return variables.get(key);
    }

    public AgentContext put(String key, Object value) {
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
        return this;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return isCancelRequested() || isExpired();
    }

    public boolean isCancelRequested() {
        return cancelled.get();
    }

    public boolean isExpired() {
        return deadline != null && !Instant.now().isBefore(deadline);
    }

    public Duration remaining() {
        if (deadline == null) {
            return Duration.ofDays(365);
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
