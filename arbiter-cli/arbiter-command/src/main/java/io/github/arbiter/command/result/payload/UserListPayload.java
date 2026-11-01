package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;
import io.github.arbiter.core.service.UserInfo;

import java.util.List;
import java.util.Map;

public record UserListPayload(List<UserPayload> users) implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        return Map.of("users", users.stream().map(UserPayload::toData).toList());
    }
}
