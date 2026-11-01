package io.github.arbiter.server.auth;

import io.github.arbiter.server.auth.PasswordHasher;

import java.util.Objects;

public final class UserAccount {
    private final String username;
    private final String passwordHash;
    private final String scope;
    private final boolean enabled;

    public UserAccount(String username, String passwordHash, String scope, boolean enabled) {
        this.username = requireText(username, "username");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.scope = requireText(scope, "scope");
        this.enabled = enabled;
    }

    public static UserAccount create(String username, String password, String scope,
                                     boolean enabled) {
        return new UserAccount(username, PasswordHasher.hash(password), scope, enabled);
    }

    public String username() {
        return username;
    }

    public String scope() {
        return scope;
    }

    String passwordHash() {
        return passwordHash;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean matches(String password) {
        return PasswordHasher.verify(password, passwordHash);
    }

    public UserAccount withChanges(String password, String newScope,
                                   Boolean newEnabled) {
        return new UserAccount(
                username,
                password == null ? passwordHash : PasswordHasher.hash(password),
                newScope == null ? scope : newScope,
                newEnabled == null ? enabled : newEnabled);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserAccount account
                && username.equals(account.username)
                && passwordHash.equals(account.passwordHash)
                && scope.equals(account.scope)
                && enabled == account.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, passwordHash, scope, enabled);
    }

    @Override
    public String toString() {
        return username;
    }
}
