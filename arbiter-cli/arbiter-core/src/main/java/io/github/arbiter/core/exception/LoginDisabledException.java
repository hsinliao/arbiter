package io.github.arbiter.core.exception;

public final class LoginDisabledException extends AuthorizationException {
    public LoginDisabledException(String serverCode, String serverMessage) {
        super(serverCode, serverMessage);
    }

    @Override
    public int exitCode() {
        return 3;
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.LOGIN_DISABLED;
    }

    @Override
    public String messageKey() {
        return "err.login.disabled";
    }
}
