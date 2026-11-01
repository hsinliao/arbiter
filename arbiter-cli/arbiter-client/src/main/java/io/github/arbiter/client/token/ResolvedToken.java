package io.github.arbiter.client.token;

import io.github.arbiter.core.service.TokenSource;

public record ResolvedToken(TokenSource source, StoredToken token) {
}
