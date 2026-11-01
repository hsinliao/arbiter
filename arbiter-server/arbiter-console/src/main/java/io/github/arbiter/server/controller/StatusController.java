package io.github.arbiter.server.controller;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.SystemStatusResponse;
import io.github.arbiter.server.security.RequireScope;
import io.github.arbiter.server.service.StatusService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StatusController {
    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping(ApiRoutes.STATUS)
    @RequireScope({"reader", "admin"})
    SystemStatusResponse status() {
        return statusService.status();
    }
}
