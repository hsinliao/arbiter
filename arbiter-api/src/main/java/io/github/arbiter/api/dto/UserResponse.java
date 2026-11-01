package io.github.arbiter.api.dto;

public record UserResponse(String username, String scope, boolean enabled) {
}
