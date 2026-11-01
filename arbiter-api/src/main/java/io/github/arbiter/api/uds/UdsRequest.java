package io.github.arbiter.api.uds;

import java.util.Map;

/**
 * Frame payload for a Unix-domain socket request. {@code body} is JSON text;
 * keeping it as text lets both client and server use Jackson without making
 * the shared API module depend on jackson-databind.
 */
public record UdsRequest(String method, String path,
                         Map<String, String> headers, String body) {
    public UdsRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
