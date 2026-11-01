package io.github.arbiter.core.service;

public record TokenIssueResult(String token, String username, String scope,
                               String expiresAt) {
}
