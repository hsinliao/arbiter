package io.github.arbiter.server.service;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.dto.QueryResponse;
import io.github.arbiter.server.error.ApiException;

import org.springframework.stereotype.Service;

@Service
public final class QueryService {
    public QueryResponse query(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new ApiException(400, ApiErrorCodes.INVALID_ARGUMENT,
                    "Query parameter 'name' must contain 1-100 characters.");
        }
        return new QueryResponse(name, "resource:" + name);
    }
}
