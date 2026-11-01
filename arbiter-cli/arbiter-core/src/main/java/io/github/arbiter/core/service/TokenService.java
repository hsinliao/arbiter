package io.github.arbiter.core.service;

import io.github.arbiter.core.exception.DomainException;

/**
 * Mints a token from username and password without persisting it. The
 * application-level login flow remains {@link AuthService#login}.
 */
public interface TokenService {
    TokenIssueResult issueToken(String username, String password) throws DomainException;
}
