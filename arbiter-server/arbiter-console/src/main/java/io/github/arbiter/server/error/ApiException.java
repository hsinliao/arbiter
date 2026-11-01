package io.github.arbiter.server.error;

import java.util.Map;

public final class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final String safeMessage;
    private final Map<String, String> headers;

    public ApiException(int status, String code, String safeMessage) {
        this(status, code, safeMessage, Map.of());
    }

    public ApiException(int status, String code, String safeMessage,
                        Map<String, String> headers) {
        super(safeMessage);
        this.status = status;
        this.code = code;
        this.safeMessage = safeMessage;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String safeMessage() {
        return safeMessage;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
