package io.github.arbiter.server.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test-only repository fixture. Production uses {@link FileUserRepository}. */
public final class InMemoryUserRepository implements MutableUserRepository {
    private final Map<String, UserAccount> users;

    public InMemoryUserRepository(Iterable<UserAccount> accounts) {
        Map<String, UserAccount> values = new LinkedHashMap<>();
        for (UserAccount account : accounts) {
            if (values.putIfAbsent(account.username(), account) != null) {
                throw new IllegalArgumentException("Duplicate server user: "
                        + account.username());
            }
        }
        this.users = values;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public boolean hasUsers() {
        return !users.isEmpty();
    }

    @Override
    public List<UserAccount> findAll() {
        return List.copyOf(users.values());
    }

    @Override
    public void add(UserAccount account) {
        if (users.putIfAbsent(account.username(), account) != null) {
            throw new IllegalArgumentException("Duplicate server user: "
                    + account.username());
        }
    }

    @Override
    public void update(UserAccount account) {
        if (!users.containsKey(account.username())) {
            throw new IllegalArgumentException("Unknown server user: "
                    + account.username());
        }
        users.put(account.username(), account);
    }

    @Override
    public boolean delete(String username) {
        return users.remove(username) != null;
    }
}
