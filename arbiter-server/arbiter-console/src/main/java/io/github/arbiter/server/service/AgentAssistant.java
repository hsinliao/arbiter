package io.github.arbiter.server.service;

import io.github.arbiter.api.dto.AgentCapabilitiesResponse;
import io.github.arbiter.api.dto.AgentRunRequest;
import io.github.arbiter.api.dto.AgentRunResponse;
import io.github.arbiter.server.auth.TokenService.TokenClaims;

/** Application boundary used by HTTP and Unix-socket transports. */
public interface AgentAssistant {
    AgentCapabilitiesResponse capabilities();

    AgentRunResponse submit(TokenClaims claims, AgentRunRequest request);

    AgentRunResponse get(TokenClaims claims, String runId);
}
