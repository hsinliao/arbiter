package io.github.arbiter.client.token;

import java.util.Optional;

public interface TokenStore {
    Optional<ResolvedToken> resolve();

    boolean exists();

    void save(StoredToken token);

    boolean delete();
}
