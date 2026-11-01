package io.github.arbiter.core.exception;

public class UsageException extends DomainException {
    public UsageException(String messageKey, Object... args) {
        super(ErrorCode.USAGE, messageKey, args);
    }
}
