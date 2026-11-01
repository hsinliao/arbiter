package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record LoginPayload(String username, String expiresAt, boolean updatedExisting)
        implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("expiresAt", expiresAt);
        data.put("updatedExisting", updatedExisting);
        return data;
    }
}
