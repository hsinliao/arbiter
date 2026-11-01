package io.github.arbiter.core.service;

import io.github.arbiter.core.exception.DomainException;

public record VerificationResult(State state, String subject, String expiresAt,
                                 DomainException error) {
    public enum State {
        VALID,
        INVALID,
        UNVERIFIABLE
    }
}
