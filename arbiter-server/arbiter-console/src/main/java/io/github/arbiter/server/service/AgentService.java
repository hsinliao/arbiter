package io.github.arbiter.server.service;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.dto.AgentCapabilitiesResponse;
import io.github.arbiter.api.dto.AgentRunRequest;
import io.github.arbiter.api.dto.AgentRunResponse;
import io.github.arbiter.api.dto.AgentTraceEventResponse;
import io.github.arbiter.server.agent.state.AgentStateException;
import io.github.arbiter.server.agent.state.AgentStateRepository;
import io.github.arbiter.server.agent.state.StoredRun;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.config.AgentProperties;
import io.github.arbiter.server.error.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * API-facing assistant boundary. It only validates and queues work; execution
 * belongs to AgentWorker and durable state belongs to AgentStateRepository.
 */
public final class AgentService implements AgentAssistant {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(token|password|secret|api[_-]?key)\\s*[=:]\\s*[^\\s,;]+");
    private static final int MAX_FAILURE_CHARS = 500;

    private final AgentProperties properties;
    private final AgentStateRepository repository;
    private final Clock clock;

    public AgentService(AgentProperties properties,
                        AgentStateRepository repository,
                        Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public AgentCapabilitiesResponse capabilities() {
        boolean enabled = properties.isEnabled() && repository != null;
        return new AgentCapabilitiesResponse(
                enabled,
                enabled ? properties.getModel() : "",
                "read-only",
                enabled ? "Assistant is configured."
                        : "Assistant is disabled. Set arbiter.agent.enabled=true and configure the model.");
    }

    @Override
    public AgentRunResponse submit(TokenClaims claims, AgentRunRequest request) {
        AgentStateRepository state = requireRepository();
        String owner = requireOwner(claims);
        String message = validateMessage(request);
        try {
            StoredRun queued = state.enqueue(
                    owner, claims.scope(), request.conversationId(), message,
                    clock.instant(), properties.getMaxRunsPerMinute(),
                    properties.getDailyTokenBudget()).run();
            return snapshot(queued);
        } catch (AgentStateException failure) {
            throw mapStateFailure(failure);
        }
    }

    @Override
    public AgentRunResponse get(TokenClaims claims, String runId) {
        AgentStateRepository state = requireRepository();
        String owner = requireOwner(claims);
        StoredRun run = state.findRun(runId)
                .filter(candidate -> candidate.owner().equals(owner))
                .orElseThrow(() -> new ApiException(
                        404, ApiErrorCodes.NOT_FOUND, "Agent run was not found."));
        return snapshot(run);
    }

    private AgentRunResponse snapshot(StoredRun run) {
        List<AgentTraceEventResponse> events = repository.events(run.runId()).stream()
                .map(event -> new AgentTraceEventResponse(
                        event.sequence(), event.type().name(),
                        event.occurredAt().toString(),
                        safeAttributes(event.attributes())))
                .toList();
        return new AgentRunResponse(
                run.runId(), run.conversationId(), run.status(),
                nullToEmpty(run.output()), safeFailure(run.failure()),
                run.createdAt().toString(),
                run.finishedAt() == null ? null : run.finishedAt().toString(),
                run.iterations(), run.inputTokens(), run.outputTokens(),
                run.cost() == null ? BigDecimal.ZERO : run.cost(), events);
    }

    private AgentStateRepository requireRepository() {
        if (!properties.isEnabled() || repository == null) {
            throw new ApiException(503, ApiErrorCodes.AGENT_UNAVAILABLE,
                    "The Console assistant is not configured.");
        }
        return repository;
    }

    private static ApiException mapStateFailure(AgentStateException failure) {
        return switch (failure.kind()) {
            case CONVERSATION_BUSY -> new ApiException(
                    409, ApiErrorCodes.CONFLICT, failure.getMessage());
            case RATE_LIMITED -> new ApiException(
                    429, ApiErrorCodes.AGENT_BUSY, failure.getMessage());
            case BUDGET_EXCEEDED -> new ApiException(
                    429, ApiErrorCodes.AGENT_BUDGET_EXCEEDED, failure.getMessage());
            case CONVERSATION_INVALID -> new ApiException(
                    400, ApiErrorCodes.INVALID_ARGUMENT, failure.getMessage());
        };
    }

    private static String requireOwner(TokenClaims claims) {
        if (claims == null || claims.subject() == null || claims.subject().isBlank()) {
            throw new ApiException(401, ApiErrorCodes.AUTH_REQUIRED,
                    "ARBITER_TOKEN is required.");
        }
        return claims.subject();
    }

    private String validateMessage(AgentRunRequest request) {
        if (request == null) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Request body must be a JSON object.");
        }
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isEmpty()) {
            throw new ApiException(400, ApiErrorCodes.INVALID_ARGUMENT,
                    "Field 'message' is required.");
        }
        if (message.length() > properties.getMaxRequestChars()) {
            throw new ApiException(400, ApiErrorCodes.INVALID_ARGUMENT,
                    "Field 'message' exceeds " + properties.getMaxRequestChars()
                            + " characters.");
        }
        return message;
    }

    private static String safeFailure(String failure) {
        if (failure == null || failure.isBlank()) {
            return "";
        }
        String sanitized = SECRET.matcher(failure)
                .replaceAll(match -> match.group(1) + "=***");
        return sanitized.length() <= MAX_FAILURE_CHARS
                ? sanitized : sanitized.substring(0, MAX_FAILURE_CHARS) + "...";
    }

    private static Map<String, Object> safeAttributes(Map<String, Object> attributes) {
        Map<String, Object> safe = new LinkedHashMap<>();
        attributes.forEach((key, value) -> safe.put(key, safeValue(value)));
        return Map.copyOf(safe);
    }

    private static Object safeValue(Object value) {
        if (value instanceof String text) {
            return safeFailure(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) ->
                    nested.put(String.valueOf(key), safeValue(nestedValue)));
            return Map.copyOf(nested);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AgentService::safeValue).toList();
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
