package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record TokenPayload(String token, String username, String scope,
                           String expiresAt) implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", username);
        data.put("scope", scope);
        data.put("expiresAt", expiresAt);
        return data;
    }
}
