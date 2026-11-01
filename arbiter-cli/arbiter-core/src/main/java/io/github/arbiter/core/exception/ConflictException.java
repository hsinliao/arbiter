package io.github.arbiter.core.exception;

public class ConflictException extends DomainException {
    public ConflictException(String serverCode, String serverMessage) {
        super(ErrorCode.CONFLICT, "err.conflict", serverCode, serverMessage);
    }
}
