package io.github.arbiter.core.exception;

public class NotFoundException extends DomainException {
    public NotFoundException(String serverCode, String serverMessage) {
        super(ErrorCode.NOT_FOUND, "err.notfound", serverCode, serverMessage);
    }
}
