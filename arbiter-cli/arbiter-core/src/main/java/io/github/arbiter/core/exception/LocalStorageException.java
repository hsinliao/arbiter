package io.github.arbiter.core.exception;

/**
 * A local credential/configuration storage failure. It must not be reported as
 * a remote or authentication failure: the server may be perfectly reachable.
 */
public final class LocalStorageException extends DomainException {
    public LocalStorageException(String detail) {
        super(ErrorCode.STORAGE, "err.storage", detail);
    }

    public LocalStorageException(String detail, Throwable cause) {
        this(detail);
        if (cause != null) {
            initCause(cause);
        }
    }
}
