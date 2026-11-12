package io.github.arbiter.server.controller;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.AgentCapabilitiesResponse;
import io.github.arbiter.api.dto.AgentRunRequest;
import io.github.arbiter.api.dto.AgentRunResponse;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.security.AuthAttributes;
import io.github.arbiter.server.security.RequireScope;
import io.github.arbiter.server.service.AgentAssistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireScope({"reader", "admin"})
public final class AgentController {
    private final AgentAssistant assistant;

    public AgentController(AgentAssistant assistant) {
        this.assistant = assistant;
    }

    @GetMapping(ApiRoutes.AGENT_CAPABILITIES)
    AgentCapabilitiesResponse capabilities() {
        return assistant.capabilities();
    }

    @PostMapping(ApiRoutes.AGENT_RUNS)
    @ResponseStatus(HttpStatus.ACCEPTED)
    AgentRunResponse submit(
            @RequestBody AgentRunRequest request,
            @RequestAttribute(AuthAttributes.CLAIMS) TokenClaims claims
    ) {
        return assistant.submit(claims, request);
    }

    @GetMapping(ApiRoutes.AGENT_RUNS + "/{runId}")
    AgentRunResponse get(
            @PathVariable String runId,
            @RequestAttribute(AuthAttributes.CLAIMS) TokenClaims claims
    ) {
        return assistant.get(claims, runId);
    }
}
