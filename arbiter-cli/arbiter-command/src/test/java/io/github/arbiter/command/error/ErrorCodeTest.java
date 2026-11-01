package io.github.arbiter.command.error;

import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.exception.ErrorCode;
import io.github.arbiter.core.exception.LocalStorageException;
import io.github.arbiter.core.exception.LoginCredentialsException;
import io.github.arbiter.core.exception.LoginRateLimitException;
import io.github.arbiter.core.exception.LoginUnavailableException;
import io.github.arbiter.core.exception.ServerControlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCodeTest {
    @Test
    void exceptionCodesKeepTheExistingExitAndMessageContract() {
        AuthenticationException auth = new AuthenticationException("AUTH", "expired");
        assertEquals(ErrorCode.AUTHENTICATION, auth.errorCode());
        assertEquals(3, auth.exitCode());
        assertEquals("err.auth.expired", auth.messageKey());

        LoginCredentialsException credentials =
                new LoginCredentialsException("AUTH", "bad credentials");
        assertEquals(ErrorCode.LOGIN_CREDENTIALS, credentials.errorCode());
        assertEquals(3, credentials.exitCode());
        assertEquals("err.login.credentials", credentials.messageKey());

        LoginRateLimitException rateLimit =
                new LoginRateLimitException("RATE", "too many");
        assertEquals(ErrorCode.LOGIN_RATE_LIMIT, rateLimit.errorCode());
        assertEquals(6, rateLimit.exitCode());

        LocalStorageException storage = new LocalStorageException("disk");
        assertEquals(ErrorCode.STORAGE, storage.errorCode());
        assertEquals(2, storage.exitCode());

        ServerControlException lifecycle = new ServerControlException("err.server.control");
        assertEquals(ErrorCode.SERVER_CONTROL, lifecycle.errorCode());
        assertEquals(1, lifecycle.exitCode());
    }

    @Test
    void loginUnavailableKeepsItsThreeTransportCategories() {
        assertEquals(ErrorCode.LOGIN_UNAVAILABLE,
                LoginUnavailableException.server("CODE", "message").errorCode());
        assertEquals(ErrorCode.LOGIN_NETWORK,
                LoginUnavailableException.network().errorCode());
        assertEquals(ErrorCode.UNKNOWN,
                LoginUnavailableException.unknown("CODE", "message").errorCode());
    }

    @Test
    void exitCodeFallbackMapsKnownCategories() {
        assertEquals(ErrorCode.USAGE, ErrorCode.fromExitCode(2));
        assertEquals(ErrorCode.AUTHENTICATION, ErrorCode.fromExitCode(3));
        assertEquals(ErrorCode.NETWORK, ErrorCode.fromExitCode(8));
        assertEquals(ErrorCode.USER_ABORTED, ErrorCode.fromExitCode(9));
        assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromExitCode(99));
    }
}
