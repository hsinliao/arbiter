package io.github.arbiter.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentCapabilitiesResponse(
        boolean enabled,
        String model,
        String mode,
        String message
) {
}
