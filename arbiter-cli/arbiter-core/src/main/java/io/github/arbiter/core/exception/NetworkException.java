package io.github.arbiter.core.exception;

public class NetworkException extends DomainException {
    public NetworkException() {
        super(ErrorCode.NETWORK, "err.network");
    }

    public NetworkException(Throwable cause) {
        this();
        if (cause != null) {
            initCause(cause);
        }
    }
}
