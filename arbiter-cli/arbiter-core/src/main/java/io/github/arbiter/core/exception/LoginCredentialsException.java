package io.github.arbiter.core.exception;

public final class LoginCredentialsException extends AuthenticationException {
    public LoginCredentialsException(String serverCode, String serverMessage) {
        super(serverCode, serverMessage);
    }

    @Override
    public int exitCode() {
        return 3;
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.LOGIN_CREDENTIALS;
    }

    @Override
    public String messageKey() {
        return "err.login.credentials";
    }
}
