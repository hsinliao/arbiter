package io.github.arbiter.client;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.SystemStatusResponse;
import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.token.ResolvedToken;
import io.github.arbiter.client.token.TokenStore;
import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.service.SystemService;
import io.github.arbiter.core.service.SystemStatus;

public final class ApiSystemService implements SystemService {
    private final ApiClient client;
    private final TokenStore tokenStore;

    public ApiSystemService(ApiClient client, TokenStore tokenStore) {
        this.client = client;
        this.tokenStore = tokenStore;
    }

    @Override
    public SystemStatus status() {
        ResolvedToken resolved = tokenStore.resolve()
                .orElseThrow(() -> new AuthenticationException(null, null));
        SystemStatusResponse response = client.get(ApiRoutes.STATUS,
                resolved.token().token(), SystemStatusResponse.class);
        if (response == null) {
            return new SystemStatus("", "", "");
        }
        return new SystemStatus(response.state(), response.version(), response.serverTime());
    }
}
