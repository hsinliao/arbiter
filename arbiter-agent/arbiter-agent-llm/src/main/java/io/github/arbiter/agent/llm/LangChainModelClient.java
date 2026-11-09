package io.github.arbiter.agent.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.llm.ModelClient;
import io.github.arbiter.agent.spi.model.ModelFinishReason;
import io.github.arbiter.agent.spi.model.MessageRole;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.model.Usage;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LangChainModelClient implements ModelClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatModel model;

    public LangChainModelClient(ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public ModelResponse chat(ModelRequest request, InvocationContext context) {
        context.checkActive();
        try {
            return toArbiterResponse(model.chat(toLangChainRequest(request)));
        } catch (ProviderException error) {
            throw error;
        } catch (RuntimeException error) {
            throw LangChainErrors.translate(error);
        }
    }

    static List<ChatMessage> toLangChainMessages(List<ModelMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        for (ModelMessage message : messages) {
            switch (message.role()) {
                case SYSTEM -> result.add(SystemMessage.from(message.content()));
                case USER -> result.add(UserMessage.from(message.content()));
                case ASSISTANT -> {
                    List<ToolExecutionRequest> calls = message.toolCalls().stream()
                            .map(LangChainModelClient::toLangChainToolRequest)
                            .toList();
                    result.add(calls.isEmpty()
                            ? AiMessage.from(message.content())
                            : new AiMessage(message.content(), calls));
                }
                case TOOL -> result.add(ToolExecutionResultMessage.from(
                        message.toolCallId(), message.toolName(), message.content()));
            }
        }
        return result;
    }

    private static ToolExecutionRequest toLangChainToolRequest(ToolCall call) {
        return ToolExecutionRequest.builder()
                .id(call.id())
                .name(call.name())
                .arguments(JSON.writeValueAsString(call.arguments()))
                .build();
    }

    static List<ToolSpecification> toToolSpecifications(List<ToolDefinition> tools) {
        return tools.stream()
                .map(definition -> {
                    Map<String, Object> spec = new LinkedHashMap<>();
                    spec.put("name", definition.name());
                    spec.put("description", definition.description());
                    spec.put("parameters", definition.parameters());
                    return ToolSpecification.fromJson(JSON.writeValueAsString(spec));
                })
                .toList();
    }

    static ModelResponse toArbiterResponse(ChatResponse response) {
        if (response == null) {
            throw ProviderException.transientFailure(
                    "LangChain4j model returned a null response", null);
        }
        AiMessage aiMessage = response.aiMessage();
        List<ToolCall> calls = aiMessage.toolExecutionRequests().stream()
                .map(request -> ToolCall.builder()
                        .id(request.id())
                        .name(request.name())
                        .arguments(parseArguments(request.arguments()))
                        .build())
                .toList();
        TokenUsage tokenUsage = response.tokenUsage();
        Usage usage = tokenUsage == null ? null : Usage.builder()
                .inputTokens(tokenUsage.inputTokenCount())
                .outputTokens(tokenUsage.outputTokenCount())
                .totalTokens(tokenUsage.totalTokenCount())
                .build();
        return ModelResponse.builder()
                .content(aiMessage.text())
                .modelId(response.modelName())
                .toolCalls(calls)
                .usage(usage)
                .finishReason(toFinishReason(response.finishReason()))
                .build();
    }

    static ChatRequest toLangChainRequest(ModelRequest request) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(toLangChainMessages(request.messages()));
        if (request.modelId() != null) {
            builder.modelName(request.modelId());
        }
        if (request.sampling().temperature() != null) {
            builder.temperature(request.sampling().temperature());
        }
        if (request.sampling().topP() != null) {
            builder.topP(request.sampling().topP());
        }
        if (request.sampling().maxTokens() != null) {
            builder.maxOutputTokens(request.sampling().maxTokens());
        }
        if (!request.tools().isEmpty()) {
            builder.toolSpecifications(toToolSpecifications(request.tools()));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(String arguments) {
        return JSON.readValue(
                arguments == null || arguments.isBlank() ? "{}" : arguments,
                Map.class);
    }

    private static ModelFinishReason toFinishReason(FinishReason reason) {
        if (reason == null) {
            return ModelFinishReason.UNKNOWN;
        }
        return switch (reason.name()) {
            case "STOP" -> ModelFinishReason.STOP;
            case "LENGTH" -> ModelFinishReason.LENGTH;
            case "TOOL_EXECUTION" -> ModelFinishReason.TOOL_CALLS;
            case "CONTENT_FILTER" -> ModelFinishReason.CONTENT_FILTER;
            default -> ModelFinishReason.UNKNOWN;
        };
    }
}
