package io.github.arbiter.core.service;

public record AuthStatus(TokenSource source, String tokenHint, VerificationResult verification) {
}
