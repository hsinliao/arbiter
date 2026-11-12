package io.github.arbiter.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentTraceEventResponse(
        long sequence,
        String type,
        String occurredAt,
        Map<String, Object> attributes
) {
}
