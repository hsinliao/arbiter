package io.github.arbiter.core.exception;

public class ServerException extends DomainException {
    public ServerException(String serverCode, String serverMessage) {
        super(ErrorCode.SERVER_ERROR, "err.server.5xx", serverCode, serverMessage);
    }
}
