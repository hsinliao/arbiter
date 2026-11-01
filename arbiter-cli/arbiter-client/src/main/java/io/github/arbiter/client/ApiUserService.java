package io.github.arbiter.client;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.UserCreateRequest;
import io.github.arbiter.api.dto.UserListResponse;
import io.github.arbiter.api.dto.UserResponse;
import io.github.arbiter.api.dto.UserUpdateRequest;
import io.github.arbiter.client.token.ResolvedToken;
import io.github.arbiter.client.token.TokenStore;
import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.exception.RemoteException;
import io.github.arbiter.core.service.UserInfo;
import io.github.arbiter.core.service.UserService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ApiUserService implements UserService {
    private final ApiClient client;
    private final TokenStore tokenStore;

    public ApiUserService(ApiClient client, TokenStore tokenStore) {
        this.client = client;
        this.tokenStore = tokenStore;
    }

    @Override
    public List<UserInfo> list() {
        UserListResponse response = client.get(ApiRoutes.USERS, token(),
                UserListResponse.class);
        return response == null || response.users() == null ? List.of()
                : response.users().stream().map(ApiUserService::toInfo).toList();
    }

    @Override
    public UserInfo show(String username) {
        return require(client.get(userPath(username), token(), UserResponse.class));
    }

    @Override
    public UserInfo create(String username, String password, String scope,
                           boolean enabled) {
        UserResponse response = client.post(ApiRoutes.USERS, token(),
                new UserCreateRequest(username, password, scope, enabled),
                UserResponse.class, List.of(password));
        return require(response);
    }

    @Override
    public UserInfo update(String username, String password, String scope,
                           Boolean enabled) {
        UserResponse response = client.put(userPath(username), token(),
                new UserUpdateRequest(password, scope, enabled), UserResponse.class);
        return require(response);
    }

    @Override
    public void delete(String username) {
        client.delete(userPath(username), token());
    }

    private String token() {
        ResolvedToken resolved = tokenStore.resolve()
                .orElseThrow(() -> new AuthenticationException(null, null));
        return resolved.token().token();
    }

    private static String userPath(String username) {
        return ApiRoutes.USERS + "/"
                + URLEncoder.encode(username, StandardCharsets.UTF_8);
    }

    private static UserInfo require(UserResponse response) {
        if (response == null) {
            throw new RemoteException(null, null);
        }
        return toInfo(response);
    }

    private static UserInfo toInfo(UserResponse response) {
        return new UserInfo(response.username(), response.scope(), response.enabled());
    }
}
