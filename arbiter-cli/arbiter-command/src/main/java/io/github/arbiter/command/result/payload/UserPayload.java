package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;
import io.github.arbiter.core.service.UserInfo;

import java.util.LinkedHashMap;
import java.util.Map;

public record UserPayload(String username, String scope, boolean enabled)
        implements ResultPayload {
    public static UserPayload from(UserInfo user) {
        return new UserPayload(user.username(), user.scope(), user.enabled());
    }

    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("scope", scope);
        data.put("enabled", enabled);
        return data;
    }
}
