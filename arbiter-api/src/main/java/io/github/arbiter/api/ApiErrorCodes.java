package io.github.arbiter.api;

public final class ApiErrorCodes {
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String TOO_MANY_LOGIN_ATTEMPTS = "TOO_MANY_LOGIN_ATTEMPTS";
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String FORBIDDEN_SCOPE = "FORBIDDEN_SCOPE";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String AGENT_UNAVAILABLE = "AGENT_UNAVAILABLE";
    public static final String AGENT_BUSY = "AGENT_BUSY";
    public static final String AGENT_BUDGET_EXCEEDED = "AGENT_BUDGET_EXCEEDED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ApiErrorCodes() {
    }
}
