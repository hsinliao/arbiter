package io.github.arbiter.agent.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final Map<String, Circuit> circuits = new ConcurrentHashMap<>();

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration == null ? Duration.ofSeconds(30) : openDuration;
        if (this.openDuration.isNegative()) {
            throw new IllegalArgumentException("openDuration must not be negative");
        }
    }

    public static CircuitBreaker defaults() {
        return new CircuitBreaker(5, Duration.ofSeconds(30));
    }

    public <T> T call(String key, Supplier<T> operation) {
        if (key == null || key.isBlank() || operation == null) {
            throw new IllegalArgumentException("Circuit key and operation are required");
        }
        Circuit circuit = circuits.computeIfAbsent(key, ignored -> new Circuit());
        synchronized (circuit) {
            if (circuit.openUntil != null) {
                if (Instant.now().isBefore(circuit.openUntil)) {
                    throw new CircuitOpenException("Circuit is open for " + key);
                }
                circuit.halfOpen = true;
                circuit.openUntil = null;
            } else if (circuit.halfOpen) {
                throw new CircuitOpenException("Circuit probe already in flight for " + key);
            }
        }
        try {
            T result = operation.get();
            synchronized (circuit) {
                circuit.failures = 0;
                circuit.halfOpen = false;
            }
            return result;
        } catch (RuntimeException | Error error) {
            synchronized (circuit) {
                circuit.failures++;
                if (circuit.halfOpen || circuit.failures >= failureThreshold) {
                    circuit.openUntil = Instant.now().plus(openDuration);
                    circuit.halfOpen = false;
                }
            }
            throw error;
        }
    }

    public void reset(String key) {
        circuits.remove(key);
    }

    public boolean isOpen(String key) {
        Circuit circuit = circuits.get(key);
        if (circuit == null) {
            return false;
        }
        synchronized (circuit) {
            return circuit.halfOpen
                    || (circuit.openUntil != null && Instant.now().isBefore(circuit.openUntil));
        }
    }

    private static final class Circuit {
        private int failures;
        private Instant openUntil;
        private boolean halfOpen;
    }

    public static final class CircuitOpenException extends RuntimeException {
        public CircuitOpenException(String message) {
            super(message);
        }
    }
}
