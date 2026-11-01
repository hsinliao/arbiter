package io.github.arbiter.client.token;

public record StoredToken(String token, String username, String expiresAt, String scope) {
}
