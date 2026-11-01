package io.github.arbiter.server.controller;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.QueryResponse;
import io.github.arbiter.server.security.RequireScope;
import io.github.arbiter.server.service.QueryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class QueryController {
    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(ApiRoutes.QUERY)
    @RequireScope({"reader", "admin"})
    QueryResponse query(@RequestParam(name = "name", required = false) String name) {
        return queryService.query(name);
    }
}
