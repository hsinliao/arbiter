package io.github.arbiter.client.transport;

import java.util.Map;

public record TransportResponse(int status, Map<String, String> headers, String body) {
    public TransportResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
