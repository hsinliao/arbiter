package io.github.arbiter.core.exception;

import java.util.Objects;

/**
 * Base exception for errors that cross the command/client boundary.
 * Text is deliberately not formatted here; rendering resolves messageKey and args.
 */
public class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String messageKey;
    private final Object[] args;
    private final String serverCode;
    private final String serverMessage;

    public DomainException(int exitCode, String messageKey, Object... args) {
        this(ErrorCode.fromExitCode(exitCode), messageKey, null, null, args);
    }

    public DomainException(int exitCode, String messageKey, String serverCode,
                           String serverMessage, Object... args) {
        this(ErrorCode.fromExitCode(exitCode), messageKey, serverCode, serverMessage, args);
    }

    public DomainException(ErrorCode errorCode, String messageKey, Object... args) {
        this(errorCode, messageKey, null, null, args);
    }

    public DomainException(ErrorCode errorCode, String messageKey, String serverCode,
                           String serverMessage, Object... args) {
        super(messageKey);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
        this.args = args == null ? new Object[0] : args.clone();
        this.serverCode = serverCode;
        this.serverMessage = serverMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int exitCode() {
        return errorCode.exitCode();
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] args() {
        return args.clone();
    }

    public String serverCode() {
        return serverCode;
    }

    public String serverMessage() {
        return serverMessage;
    }
}
