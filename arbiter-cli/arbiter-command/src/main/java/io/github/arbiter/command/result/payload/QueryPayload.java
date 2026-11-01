package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record QueryPayload(String name, String value) implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("value", value);
        return data;
    }
}
