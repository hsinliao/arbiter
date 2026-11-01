package io.github.arbiter.api.dto;

public record UserCreateRequest(String username, String password, String scope,
                                Boolean enabled) {
}
