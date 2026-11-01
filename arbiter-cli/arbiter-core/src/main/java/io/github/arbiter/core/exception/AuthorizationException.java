package io.github.arbiter.core.exception;

public class AuthorizationException extends DomainException {
    public AuthorizationException(String serverCode, String serverMessage) {
        super(ErrorCode.AUTHORIZATION, "err.auth.forbidden", serverCode, serverMessage);
    }
}
