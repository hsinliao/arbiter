package io.github.arbiter.agent.spi.invocation;

import io.github.arbiter.agent.spi.error.ProviderException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record InvocationContext(
        String requestId,
        String traceId,
        String taskId,
        String sessionId,
        Instant deadline,
        BooleanSupplier cancellationRequested,
        RoutingRequirements routing,
        Map<String, Object> attributes
) {
    public InvocationContext {
        requestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString() : requestId;
        traceId = traceId == null ? "" : traceId;
        taskId = taskId == null ? "" : taskId;
        sessionId = sessionId == null ? "" : sessionId;
        cancellationRequested = cancellationRequested == null ? () -> false : cancellationRequested;
        routing = routing == null ? RoutingRequirements.unlimited(null) : routing;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static InvocationContext of(String taskId, String sessionId, Instant deadline) {
        return new InvocationContext(
                null, UUID.randomUUID().toString(), taskId, sessionId, deadline,
                () -> false, RoutingRequirements.unlimited(null), Map.of());
    }

    public InvocationContext withRequestId(String newRequestId) {
        return new InvocationContext(
                newRequestId, traceId, taskId, sessionId, deadline,
                cancellationRequested, routing, attributes);
    }

    public InvocationContext withRouting(RoutingRequirements newRouting) {
        return new InvocationContext(
                requestId, traceId, taskId, sessionId, deadline,
                cancellationRequested, newRouting, attributes);
    }

    public Optional<Duration> remainingTime() {
        if (deadline == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.isNegative() || remaining.isZero() ? Optional.empty() : Optional.of(remaining);
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.getAsBoolean();
    }

    public void checkActive() {
        if (isCancellationRequested()) {
            throw ProviderException.cancelled("Invocation cancelled");
        }
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw ProviderException.timeout("Invocation deadline exceeded");
        }
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }
}
