package io.github.arbiter.core.exception;

public final class LoginRateLimitException extends RateLimitException {
    public LoginRateLimitException(String serverCode, String serverMessage) {
        super(serverCode, serverMessage);
    }

    @Override
    public int exitCode() {
        return 6;
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.LOGIN_RATE_LIMIT;
    }

    @Override
    public String messageKey() {
        return "err.login.ratelimit";
    }
}
