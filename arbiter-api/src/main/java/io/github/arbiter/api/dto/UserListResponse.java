package io.github.arbiter.api.dto;

import java.util.List;

public record UserListResponse(List<UserResponse> users) {
}
