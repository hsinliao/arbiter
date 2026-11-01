package io.github.arbiter.server.service;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.dto.LoginRequest;
import io.github.arbiter.api.dto.LoginResponse;
import io.github.arbiter.api.dto.WhoAmIResponse;
import io.github.arbiter.server.auth.LoginRateLimiter;
import io.github.arbiter.server.auth.MutableUserRepository;
import io.github.arbiter.server.auth.TokenService;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.auth.UserAccount;
import io.github.arbiter.server.error.ApiException;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public final class AuthService {
    private final MutableUserRepository users;
    private final TokenService tokens;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(MutableUserRepository users, TokenService tokens,
                       LoginRateLimiter loginRateLimiter) {
        this.users = users;
        this.tokens = tokens;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginResponse login(LoginRequest request, String clientId) {
        if (request == null) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Request body must be a JSON object.");
        }
        String username = requiredText(request.username(), "username");
        String password = requiredText(request.password(), "password");
        String rateKey = username + "|" + (clientId == null ? "local" : clientId);
        if (loginRateLimiter.isLimited(rateKey)) {
            throw new ApiException(429, ApiErrorCodes.TOO_MANY_LOGIN_ATTEMPTS,
                    "Too many login attempts. Try again later.");
        }
        Optional<UserAccount> account = users.findByUsername(username);
        if (account.isEmpty()) {
            loginRateLimiter.recordFailure(rateKey);
            throw new ApiException(401, ApiErrorCodes.BAD_CREDENTIALS,
                    "Invalid username or password.");
        }
        if (!account.get().enabled()) {
            loginRateLimiter.recordFailure(rateKey);
            throw new ApiException(403, ApiErrorCodes.ACCOUNT_DISABLED,
                    "The account is disabled or locked.");
        }
        if (!account.get().matches(password)) {
            loginRateLimiter.recordFailure(rateKey);
            throw new ApiException(401, ApiErrorCodes.BAD_CREDENTIALS,
                    "Invalid username or password.");
        }
        loginRateLimiter.clear(rateKey);
        TokenService.IssuedToken issued = tokens.issue(account.get());
        return new LoginResponse(issued.token(), issued.expiresAt().toString(),
                account.get().username(), account.get().scope());
    }

    public WhoAmIResponse whoAmI(TokenClaims claims) {
        return new WhoAmIResponse(claims.subject(), claims.subject(),
                claims.expiresAt().toString(), claims.scope());
    }

    public TokenClaims requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(401, ApiErrorCodes.AUTH_REQUIRED,
                    "ARBITER_TOKEN is required.");
        }
        return tokens.verify(token).orElseThrow(() ->
                new ApiException(401, ApiErrorCodes.INVALID_TOKEN,
                        "ARBITER_TOKEN is invalid or expired."));
    }

    public TokenClaims requireScopes(String token, String... allowedScopes) {
        TokenClaims claims = requireToken(token);
        if (allowedScopes.length == 0 || "*".equals(claims.scope())) {
            return claims;
        }
        for (String allowedScope : allowedScopes) {
            if (allowedScope.equals(claims.scope())) {
                return claims;
            }
        }
        throw new ApiException(403, ApiErrorCodes.FORBIDDEN_SCOPE,
                "The token scope is not allowed for this endpoint.");
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Field '" + field + "' is required.");
        }
        return value;
    }
}
