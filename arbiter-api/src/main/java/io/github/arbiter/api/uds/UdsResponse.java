package io.github.arbiter.api.uds;

import java.util.Map;

public record UdsResponse(int status, Map<String, String> headers, String body) {
    public UdsResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
