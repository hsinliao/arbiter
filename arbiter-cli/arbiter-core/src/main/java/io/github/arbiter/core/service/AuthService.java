package io.github.arbiter.core.service;

import io.github.arbiter.core.exception.DomainException;

public interface AuthService {
    LoginResult login(String username, String password) throws DomainException;

    AuthStatus status() throws DomainException;

    VerificationResult verify(TokenSource source) throws DomainException;

    LogoutResult logout();

    boolean environmentTokenPresent();
}
