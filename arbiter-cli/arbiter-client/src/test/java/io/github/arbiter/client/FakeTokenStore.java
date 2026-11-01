package io.github.arbiter.client;

import io.github.arbiter.client.token.ResolvedToken;
import io.github.arbiter.client.token.StoredToken;
import io.github.arbiter.client.token.TokenStore;
import io.github.arbiter.core.service.TokenSource;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class FakeTokenStore implements TokenStore {
    private final AtomicReference<ResolvedToken> resolved = new AtomicReference<>();
    private final AtomicReference<StoredToken> saved = new AtomicReference<>();

    FakeTokenStore() {
    }

    FakeTokenStore(String token) {
        resolved.set(new ResolvedToken(TokenSource.ENV,
                new StoredToken(token, "alice", null, null)));
    }

    @Override
    public Optional<ResolvedToken> resolve() {
        return Optional.ofNullable(resolved.get());
    }

    @Override
    public boolean exists() {
        return resolved.get() != null;
    }

    @Override
    public void save(StoredToken token) {
        saved.set(token);
    }

    @Override
    public boolean delete() {
        resolved.set(null);
        saved.set(null);
        return true;
    }

    StoredToken saved() {
        return saved.get();
    }
}
