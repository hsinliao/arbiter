package io.github.arbiter.api.dto;

public record UserUpdateRequest(String password, String scope, Boolean enabled) {
}
