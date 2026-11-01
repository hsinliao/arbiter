package io.github.arbiter.client.http;

import java.util.List;

record RequestSpec(String method, String path, String token, Object body,
                   List<String> sensitiveValues) {
    static RequestSpec get(String path, String token) {
        return new RequestSpec("GET", path, token, null,
                token == null ? List.of() : List.of(token));
    }

    static RequestSpec put(String path, String token, Object body) {
        return new RequestSpec("PUT", path, token, body,
                token == null ? List.of() : List.of(token));
    }

    static RequestSpec delete(String path, String token) {
        return new RequestSpec("DELETE", path, token, null,
                token == null ? List.of() : List.of(token));
    }

    static RequestSpec post(String path, Object body, List<String> sensitiveValues) {
        return new RequestSpec("POST", path, null, body, sensitiveValues);
    }
}
