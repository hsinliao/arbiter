package io.github.arbiter.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentRunRequest(String message, String conversationId) {
}
