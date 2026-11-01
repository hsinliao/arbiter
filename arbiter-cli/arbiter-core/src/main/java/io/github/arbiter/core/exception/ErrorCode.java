package io.github.arbiter.core.exception;

/**
 * Stable machine-readable error categories. The numeric exit code remains the
 * public process contract; the code names the reason without tying callers to
 * an exception class or a localized message key.
 */
public enum ErrorCode {
    USAGE("usage", 2),
    AUTHENTICATION("authentication", 3),
    AUTHORIZATION("authorization", 3),
    LOGIN_CREDENTIALS("login_credentials", 3),
    LOGIN_DISABLED("login_disabled", 3),
    NOT_FOUND("not_found", 4),
    CONFLICT("conflict", 5),
    RATE_LIMIT("rate_limit", 6),
    LOGIN_RATE_LIMIT("login_rate_limit", 6),
    SERVER_ERROR("server_error", 7),
    LOGIN_UNAVAILABLE("login_unavailable", 7),
    NETWORK("network", 8),
    LOGIN_NETWORK("login_network", 8),
    USER_ABORTED("user_aborted", 9),
    STORAGE("storage", 2),
    SERVER_CONTROL("server_control", 1),
    UNKNOWN("unknown", 1);

    private final String code;
    private final int exitCode;

    ErrorCode(String code, int exitCode) {
        this.code = code;
        this.exitCode = exitCode;
    }

    public String code() {
        return code;
    }

    public int exitCode() {
        return exitCode;
    }

    public static ErrorCode fromExitCode(int exitCode) {
        return switch (exitCode) {
            case 1 -> UNKNOWN;
            case 2 -> USAGE;
            case 3 -> AUTHENTICATION;
            case 4 -> NOT_FOUND;
            case 5 -> CONFLICT;
            case 6 -> RATE_LIMIT;
            case 7 -> SERVER_ERROR;
            case 8 -> NETWORK;
            case 9 -> USER_ABORTED;
            default -> UNKNOWN;
        };
    }
}
