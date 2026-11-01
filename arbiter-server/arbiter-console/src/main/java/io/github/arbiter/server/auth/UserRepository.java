package io.github.arbiter.server.auth;

import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByUsername(String username);

    default boolean hasUsers() {
        return false;
    }
}
