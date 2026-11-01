package io.github.arbiter.core.exception;

public class RemoteException extends DomainException {
    public RemoteException(String serverCode, String serverMessage, Object... args) {
        super(ErrorCode.UNKNOWN, "err.unknown", serverCode, serverMessage, args);
    }
}
