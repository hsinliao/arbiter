package io.github.arbiter.core.exception;

public class UserAbortedException extends DomainException {
    public UserAbortedException() {
        super(ErrorCode.USER_ABORTED, "err.aborted");
    }
}
