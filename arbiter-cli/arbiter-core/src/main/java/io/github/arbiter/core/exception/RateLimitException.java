package io.github.arbiter.core.exception;

public class RateLimitException extends DomainException {
    public RateLimitException(String serverCode, String serverMessage) {
        super(ErrorCode.RATE_LIMIT, "err.ratelimit", serverCode, serverMessage);
    }
}
