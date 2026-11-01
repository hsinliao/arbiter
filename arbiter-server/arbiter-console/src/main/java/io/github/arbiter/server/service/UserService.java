package io.github.arbiter.server.service;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.dto.UserCreateRequest;
import io.github.arbiter.api.dto.UserListResponse;
import io.github.arbiter.api.dto.UserResponse;
import io.github.arbiter.api.dto.UserUpdateRequest;
import io.github.arbiter.server.auth.MutableUserRepository;
import io.github.arbiter.server.auth.UserAccount;
import io.github.arbiter.server.error.ApiException;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Service
public final class UserService {
    private final MutableUserRepository users;

    public UserService(MutableUserRepository users) {
        this.users = users;
    }

    public UserListResponse list() {
        List<UserResponse> values = users.findAll().stream()
                .sorted(Comparator.comparing(UserAccount::username))
                .map(UserService::toResponse)
                .toList();
        return new UserListResponse(values);
    }

    public UserResponse get(String username) {
        return toResponse(find(username));
    }

    public UserResponse create(UserCreateRequest request) {
        if (request == null) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Request body must be a JSON object.");
        }
        String username = requiredText(request.username(), "username");
        validateUsername(username);
        String password = requiredText(request.password(), "password");
        String scope = scope(request.scope());
        if (users.findByUsername(username).isPresent()) {
            throw new ApiException(409, ApiErrorCodes.CONFLICT,
                    "A user with that username already exists.");
        }
        boolean enabled = request.enabled() == null || request.enabled();
        UserAccount account = UserAccount.create(username, password, scope, enabled);
        try {
            users.add(account);
        } catch (IOException failure) {
            throw new ApiException(500, ApiErrorCodes.INTERNAL_ERROR,
                    "Unable to save the user.");
        } catch (IllegalArgumentException conflict) {
            throw new ApiException(409, ApiErrorCodes.CONFLICT, conflict.getMessage());
        }
        return toResponse(account);
    }

    public UserResponse update(String username, UserUpdateRequest request,
                               String currentUser) {
        if (request == null) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Request body must be a JSON object.");
        }
        validateUsername(username);
        UserAccount existing = find(username);
        String password = request.password() == null || request.password().isBlank()
                ? null : request.password();
        String newScope = request.scope() == null || request.scope().isBlank()
                ? null : scope(request.scope());
        if (username.equals(currentUser)
                && (Boolean.FALSE.equals(request.enabled())
                || (newScope != null && !"admin".equals(newScope)))) {
            throw new ApiException(409, ApiErrorCodes.CONFLICT,
                    "The current administrator cannot disable or demote itself.");
        }
        UserAccount changed = existing.withChanges(password, newScope, request.enabled());
        try {
            users.update(changed);
        } catch (IOException failure) {
            throw new ApiException(500, ApiErrorCodes.INTERNAL_ERROR,
                    "Unable to update the user.");
        }
        return toResponse(changed);
    }

    public UserResponse delete(String username, String currentUser) {
        validateUsername(username);
        UserAccount existing = find(username);
        if (username.equals(currentUser)) {
            throw new ApiException(409, ApiErrorCodes.CONFLICT,
                    "The current administrator cannot delete itself.");
        }
        try {
            users.delete(username);
        } catch (IOException failure) {
            throw new ApiException(500, ApiErrorCodes.INTERNAL_ERROR,
                    "Unable to delete the user.");
        }
        return toResponse(existing);
    }

    private UserAccount find(String username) {
        validateUsername(username);
        return users.findByUsername(username).orElseThrow(() ->
                new ApiException(404, ApiErrorCodes.NOT_FOUND,
                        "The requested user was not found."));
    }

    private static UserResponse toResponse(UserAccount account) {
        return new UserResponse(account.username(), account.scope(), account.enabled());
    }

    private static String scope(String value) {
        String scope = requiredText(value, "scope");
        if (scope.length() > 64) {
            throw new ApiException(400, ApiErrorCodes.INVALID_ARGUMENT,
                    "Field 'scope' must contain at most 64 characters.");
        }
        return scope;
    }

    private static void validateUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new ApiException(400, ApiErrorCodes.INVALID_ARGUMENT,
                    "Field 'username' must use 1-100 letters, digits, dot, underscore,"
                            + " or hyphen.");
        }
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Field '" + field + "' is required.");
        }
        return value;
    }
}
