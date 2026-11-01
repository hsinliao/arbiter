package io.github.arbiter.core.exception;

public final class ServerControlException extends DomainException {
    public ServerControlException(String messageKey, Object... args) {
        super(ErrorCode.SERVER_CONTROL, messageKey, args);
    }
}
