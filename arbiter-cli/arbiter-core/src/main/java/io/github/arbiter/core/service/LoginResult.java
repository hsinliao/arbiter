package io.github.arbiter.core.service;

public record LoginResult(String username, String expiresAt, boolean updatedExisting,
                          boolean environmentTokenPresent) {
}
