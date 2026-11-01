package io.github.arbiter.core.exception;

public final class LoginUnavailableException extends DomainException {
    private LoginUnavailableException(ErrorCode errorCode, String messageKey,
                                      String serverCode, String serverMessage) {
        super(errorCode, messageKey, serverCode, serverMessage,
                serverCode == null || serverCode.isBlank() ? "" : serverCode);
    }

    public static LoginUnavailableException server(String code, String message) {
        return new LoginUnavailableException(ErrorCode.LOGIN_UNAVAILABLE,
                "err.login.unavailable", code, message);
    }

    public static LoginUnavailableException network() {
        return new LoginUnavailableException(ErrorCode.LOGIN_NETWORK,
                "err.login.network", null, null);
    }

    public static LoginUnavailableException unknown(String code, String message) {
        return new LoginUnavailableException(ErrorCode.UNKNOWN,
                "err.login.unavailable", code, message);
    }
}
