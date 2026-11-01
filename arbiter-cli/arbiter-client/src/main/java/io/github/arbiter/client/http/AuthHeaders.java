package io.github.arbiter.client.http;

import io.github.arbiter.api.ApiHeaders;

import java.net.http.HttpRequest;

public final class AuthHeaders {
    public static final String ARBITER_TOKEN = ApiHeaders.ARBITER_TOKEN;

    private AuthHeaders() {
    }

    public static HttpRequest.Builder withToken(HttpRequest.Builder builder, String token) {
        return builder.header(ARBITER_TOKEN, token);
    }
}
