package io.github.arbiter.agent.spi.error;

public enum ProviderErrorKind {
    AUTHENTICATION,
    AUTHORIZATION,
    RATE_LIMIT,
    TIMEOUT,
    CANCELLED,
    INVALID_REQUEST,
    CONTEXT_LENGTH_EXCEEDED,
    CONTENT_REJECTED,
    UNAVAILABLE,
    TRANSIENT,
    INTERNAL
}
