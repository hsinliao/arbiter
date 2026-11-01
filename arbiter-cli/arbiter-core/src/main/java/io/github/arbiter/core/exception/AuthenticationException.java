package io.github.arbiter.core.exception;

public class AuthenticationException extends DomainException {
    public AuthenticationException(String serverCode, String serverMessage) {
        super(ErrorCode.AUTHENTICATION, "err.auth.expired", serverCode, serverMessage);
    }
}
