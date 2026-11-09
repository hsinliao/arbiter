package io.github.arbiter.agent.spi.error;

import java.time.Duration;

public class ProviderException extends RuntimeException {

    private final ProviderErrorKind kind;
    private final boolean retryable;
    private final Duration retryAfter;

    public ProviderException(
            ProviderErrorKind kind,
            String message,
            boolean retryable,
            Duration retryAfter,
            Throwable cause
    ) {
        super(message, cause);
        this.kind = kind == null ? ProviderErrorKind.INTERNAL : kind;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public ProviderErrorKind kind() {
        return kind;
    }

    public boolean retryable() {
        return retryable;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public static ProviderException transientFailure(String message, Throwable cause) {
        return new ProviderException(ProviderErrorKind.TRANSIENT, message, true, null, cause);
    }

    public static ProviderException timeout(String message) {
        return new ProviderException(ProviderErrorKind.TIMEOUT, message, false, null, null);
    }

    public static ProviderException cancelled(String message) {
        return new ProviderException(ProviderErrorKind.CANCELLED, message, false, null, null);
    }

    public static ProviderException invalidRequest(String message) {
        return new ProviderException(ProviderErrorKind.INVALID_REQUEST, message, false, null, null);
    }
}
