package io.github.arbiter.client;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.LoginRequest;
import io.github.arbiter.api.dto.LoginResponse;
import io.github.arbiter.api.dto.WhoAmIResponse;
import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.token.ResolvedToken;
import io.github.arbiter.client.token.StoredToken;
import io.github.arbiter.client.token.TokenStore;
import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.exception.AuthorizationException;
import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.core.exception.LoginCredentialsException;
import io.github.arbiter.core.exception.LoginDisabledException;
import io.github.arbiter.core.exception.LoginRateLimitException;
import io.github.arbiter.core.exception.LoginUnavailableException;
import io.github.arbiter.core.exception.NetworkException;
import io.github.arbiter.core.exception.RateLimitException;
import io.github.arbiter.core.exception.RemoteException;
import io.github.arbiter.core.exception.ServerException;
import io.github.arbiter.core.service.AuthService;
import io.github.arbiter.core.service.TokenIssueResult;
import io.github.arbiter.core.service.TokenService;
import io.github.arbiter.core.service.AuthStatus;
import io.github.arbiter.core.service.LoginResult;
import io.github.arbiter.core.service.LogoutResult;
import io.github.arbiter.core.service.TokenSource;
import io.github.arbiter.core.service.VerificationResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class ApiAuthService implements AuthService, TokenService {
    private final ApiClient client;
    private final TokenStore tokenStore;
    private final Supplier<String> environmentToken;

    public ApiAuthService(ApiClient client, TokenStore tokenStore,
                           Supplier<String> environmentToken) {
        this.client = client;
        this.tokenStore = tokenStore;
        this.environmentToken = environmentToken;
    }

    @Override
    public LoginResult login(String username, String password) {
        boolean hadLocal = tokenStore.exists();
        LoginResponse response = requestLogin(username, password);
        String effectiveUsername = response.username() == null
                ? username : response.username();
        tokenStore.save(new StoredToken(response.token(), effectiveUsername,
                response.expiresAt(), response.scope()));
        return new LoginResult(effectiveUsername, response.expiresAt(), hadLocal,
                environmentTokenPresent());
    }

    @Override
    public TokenIssueResult issueToken(String username, String password) {
        LoginResponse response = requestLogin(username, password);
        return new TokenIssueResult(response.token(),
                response.username() == null ? username : response.username(),
                response.scope(), response.expiresAt());
    }

    private LoginResponse requestLogin(String username, String password) {
        LoginResponse response;
        try {
            response = client.post(ApiRoutes.LOGIN, new LoginRequest(username, password),
                    LoginResponse.class, List.of(password));
        } catch (LoginCredentialsException | LoginDisabledException
                 | LoginRateLimitException | LoginUnavailableException exception) {
            throw exception;
        } catch (AuthenticationException exception) {
            throw new LoginCredentialsException(exception.serverCode(),
                    exception.serverMessage());
        } catch (AuthorizationException exception) {
            throw new LoginDisabledException(exception.serverCode(),
                    exception.serverMessage());
        } catch (RateLimitException exception) {
            throw new LoginRateLimitException(exception.serverCode(),
                    exception.serverMessage());
        } catch (ServerException exception) {
            throw LoginUnavailableException.server(exception.serverCode(),
                    exception.serverMessage());
        } catch (NetworkException exception) {
            throw LoginUnavailableException.network();
        } catch (RemoteException exception) {
            throw LoginUnavailableException.unknown(exception.serverCode(),
                    exception.serverMessage());
        }

        if (response == null || response.token() == null || response.token().isBlank()) {
            throw LoginUnavailableException.unknown(null, null);
        }
        return response;
    }

    @Override
    public AuthStatus status() {
        ResolvedToken resolved = tokenStore.resolve()
                .orElseThrow(() -> new AuthenticationException(null, null));
        VerificationResult verification = verify(resolved.source());
        return new AuthStatus(resolved.source(), tokenHint(resolved.token().token()),
                verification);
    }

    @Override
    public VerificationResult verify(TokenSource source) {
        Optional<ResolvedToken> resolved = tokenStore.resolve();
        if (resolved.isEmpty() || resolved.get().source() != source) {
            return new VerificationResult(VerificationResult.State.INVALID,
                    null, null, null);
        }
        String token = resolved.get().token().token();
        try {
            WhoAmIResponse response = client.get(ApiRoutes.WHO_AM_I, token,
                    WhoAmIResponse.class);
            String subject = response == null ? null
                    : firstNonBlank(response.subject(), response.username());
            return new VerificationResult(VerificationResult.State.VALID,
                    subject, response == null ? null : response.expiresAt(), null);
        } catch (AuthenticationException invalid) {
            return new VerificationResult(VerificationResult.State.INVALID,
                    null, null, null);
        } catch (DomainException unverifiable) {
            return new VerificationResult(VerificationResult.State.UNVERIFIABLE,
                    null, null, unverifiable);
        }
    }

    @Override
    public LogoutResult logout() {
        boolean removed = tokenStore.delete();
        return new LogoutResult(removed, environmentTokenPresent());
    }

    @Override
    public boolean environmentTokenPresent() {
        String token = environmentToken.get();
        return token != null && !token.isBlank();
    }

    private static String tokenHint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
