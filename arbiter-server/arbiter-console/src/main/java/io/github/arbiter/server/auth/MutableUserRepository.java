package io.github.arbiter.server.auth;

import java.io.IOException;
import java.util.List;

/**
 * Persistence-facing user repository contract for future administration APIs.
 */
public interface MutableUserRepository extends UserRepository {
    List<UserAccount> findAll();

    void add(UserAccount account) throws IOException;

    void update(UserAccount account) throws IOException;

    boolean delete(String username) throws IOException;
}
