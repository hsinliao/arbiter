package io.github.arbiter.command.result.payload;

import io.github.arbiter.command.result.ResultPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record EncryptedPayload(String ciphertext) implements ResultPayload {
    @Override
    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ciphertext", ciphertext);
        return data;
    }
}
