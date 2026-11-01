package io.github.arbiter.client;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.QueryResponse;
import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.token.ResolvedToken;
import io.github.arbiter.client.token.TokenStore;
import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.exception.RemoteException;
import io.github.arbiter.core.service.QueryResult;
import io.github.arbiter.core.service.QueryService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ApiQueryService implements QueryService {
    private final ApiClient client;
    private final TokenStore tokenStore;

    public ApiQueryService(ApiClient client, TokenStore tokenStore) {
        this.client = client;
        this.tokenStore = tokenStore;
    }

    @Override
    public QueryResult query(String name) {
        ResolvedToken resolved = tokenStore.resolve()
                .orElseThrow(() -> new AuthenticationException(null, null));
        String path = ApiRoutes.QUERY + "?name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
        QueryResponse response = client.get(path, resolved.token().token(), QueryResponse.class);
        if (response == null) {
            throw new RemoteException(null, null);
        }
        return new QueryResult(
                response.name() == null ? name : response.name(),
                response.value() == null ? "" : response.value());
    }
}
