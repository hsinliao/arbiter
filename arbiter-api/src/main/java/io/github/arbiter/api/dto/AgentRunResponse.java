package io.github.arbiter.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentRunResponse(
        String runId,
        String conversationId,
        String status,
        String output,
        String failure,
        String createdAt,
        String finishedAt,
        int iterations,
        int inputTokens,
        int outputTokens,
        BigDecimal cost,
        List<AgentTraceEventResponse> events
) {
}
