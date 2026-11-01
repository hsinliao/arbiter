package io.github.arbiter.client.transport;

import java.util.Map;

public record TransportRequest(String method, String path,
                               Map<String, String> headers, String body) {
    public TransportRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
