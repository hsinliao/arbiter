package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.error.ProviderErrorKind;
import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.llm.ModelClient;
import io.github.arbiter.agent.spi.model.MessageRole;
import io.github.arbiter.agent.spi.model.ModelFinishReason;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.SamplingOptions;
import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.model.Usage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Minimal OpenAI-compatible chat-completions client using Jackson 3 and the
 * JDK HTTP client and keeps the runtime on one Jackson generation.
 */
public final class OpenAiCompatibleModelClient implements ModelClient {
    private static final String USER_AGENT = "arbiter-console-agent/1";
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public OpenAiCompatibleModelClient(
            String baseUrl,
            String apiKey,
            String model,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Model base URL is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Model API key is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name is required");
        }
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.requestTimeout = positive(requestTimeout, "requestTimeout");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(positive(connectTimeout, "connectTimeout"))
                .build();
    }

    @Override
    public ModelResponse chat(ModelRequest request, InvocationContext context) {
        context.checkActive();
        Duration timeout = context.remainingTime()
                .map(remaining -> remaining.compareTo(requestTimeout) < 0
                        ? remaining : requestTimeout)
                .orElse(requestTimeout);
        if (timeout.isZero() || timeout.isNegative()) {
            throw ProviderException.timeout("Model call deadline exceeded");
        }
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(requestPayload(request)),
                            StandardCharsets.UTF_8))
                    .build();
        } catch (JacksonException invalid) {
            throw ProviderException.invalidRequest(
                    "Unable to serialize the model request");
        }

        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> response;
        try {
            response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutError) {
            future.cancel(true);
            throw ProviderException.timeout("Model call timed out");
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ProviderException.cancelled("Model call interrupted");
        } catch (CancellationException cancelled) {
            throw ProviderException.cancelled("Model call cancelled");
        } catch (ExecutionException failure) {
            throw ProviderException.transientFailure(
                    "Model service request failed", failure.getCause());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw httpFailure(response.statusCode());
        }
        try {
            return response(mapper.readTree(response.body()), model);
        } catch (JacksonException invalid) {
            throw ProviderException.transientFailure(
                    "Model service returned invalid JSON", invalid);
        }
    }

    private ObjectNode requestPayload(ModelRequest request) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", request.modelId() == null || request.modelId().isBlank()
                ? model : request.modelId());
        ArrayNode messages = payload.putArray("messages");
        for (ModelMessage message : request.messages()) {
            messages.add(messageNode(message));
        }
        SamplingOptions sampling = request.sampling();
        if (sampling.temperature() != null) {
            payload.put("temperature", sampling.temperature());
        }
        if (sampling.topP() != null) {
            payload.put("top_p", sampling.topP());
        }
        if (sampling.maxTokens() != null) {
            payload.put("max_tokens", sampling.maxTokens());
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = payload.putArray("tools");
            request.tools().forEach(tool -> tools.add(toolNode(tool)));
        }
        return payload;
    }

    private ObjectNode messageNode(ModelMessage message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", message.role().name().toLowerCase(Locale.ROOT));
        if (message.role() == MessageRole.TOOL) {
            node.put("tool_call_id", message.toolCallId());
            node.put("name", message.toolName());
        }
        node.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            ArrayNode calls = node.putArray("tool_calls");
            for (ToolCall call : message.toolCalls()) {
                ObjectNode item = calls.addObject();
                item.put("id", call.id());
                item.put("type", "function");
                ObjectNode function = item.putObject("function");
                function.put("name", call.name());
                function.put("arguments", writeJson(call.arguments()));
            }
        }
        return node;
    }

    private ObjectNode toolNode(ToolDefinition tool) {
        ObjectNode item = mapper.createObjectNode();
        item.put("type", "function");
        ObjectNode function = item.putObject("function");
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.set("parameters", mapper.valueToTree(tool.parameters()));
        return item;
    }

    private ModelResponse response(JsonNode root, String fallbackModel) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw ProviderException.transientFailure(
                    "Model response did not contain a choice", null);
        }
        JsonNode choice = choices.path(0);
        JsonNode message = choice.path("message");
        if (!message.isObject()) {
            throw ProviderException.transientFailure(
                    "Model response did not contain a message", null);
        }
        String content = message.path("content").isNull()
                ? "" : message.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            toolCalls.add(ToolCall.builder()
                    .id(call.path("id").asText(""))
                    .name(function.path("name").asText(""))
                    .arguments(parseArguments(function.path("arguments")))
                    .build());
        }
        JsonNode usageNode = root.path("usage");
        Usage usage = usageNode.isObject()
                ? Usage.builder()
                .inputTokens(integer(usageNode, "prompt_tokens"))
                .outputTokens(integer(usageNode, "completion_tokens"))
                .totalTokens(integer(usageNode, "total_tokens"))
                .build()
                : null;
        String modelId = root.path("model").asText(fallbackModel);
        return ModelResponse.builder()
                .content(content)
                .modelId(modelId)
                .toolCalls(toolCalls)
                .usage(usage)
                .finishReason(finishReason(choice.path("finish_reason").asText("")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (node.isObject()) {
            return mapper.convertValue(node, Map.class);
        }
        String value = node.asText("");
        if (value.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(value, Map.class);
        } catch (JacksonException invalid) {
            throw ProviderException.transientFailure(
                    "Model returned invalid tool arguments", invalid);
        }
    }

    private ProviderException httpFailure(int status) {
        if (status == 401) {
            return new ProviderException(ProviderErrorKind.AUTHENTICATION,
                    "Model service rejected the API key", false, null, null);
        }
        if (status == 403) {
            return new ProviderException(ProviderErrorKind.AUTHORIZATION,
                    "Model service denied the request", false, null, null);
        }
        if (status == 429) {
            return new ProviderException(ProviderErrorKind.RATE_LIMIT,
                    "Model service rate limit reached", true, null, null);
        }
        if (status == 408) {
            return ProviderException.timeout("Model service timed out the request");
        }
        if (status >= 500) {
            return ProviderException.transientFailure(
                    "Model service is temporarily unavailable", null);
        }
        return ProviderException.invalidRequest(
                "Model service rejected the request with HTTP " + status);
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException invalid) {
            throw ProviderException.invalidRequest(
                    "Unable to serialize model tool arguments");
        }
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static ModelFinishReason finishReason(String reason) {
        return switch (reason) {
            case "stop" -> ModelFinishReason.STOP;
            case "length" -> ModelFinishReason.LENGTH;
            case "tool_calls", "function_call" -> ModelFinishReason.TOOL_CALLS;
            case "content_filter" -> ModelFinishReason.CONTENT_FILTER;
            case "error" -> ModelFinishReason.ERROR;
            default -> ModelFinishReason.UNKNOWN;
        };
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
