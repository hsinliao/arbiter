package io.github.arbiter.agent.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.arbiter.agent.spi.error.ProviderException;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.llm.ModelStreamHandle;
import io.github.arbiter.agent.spi.llm.ModelStreamObserver;
import io.github.arbiter.agent.spi.llm.StreamingModelClient;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.ToolCallDelta;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LangChainStreamingModelClient implements StreamingModelClient {

    private final StreamingChatModel model;

    public LangChainStreamingModelClient(StreamingChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public ModelResponse chat(ModelRequest request, InvocationContext context) {
        CompletableBridge bridge = new CompletableBridge();
        stream(request, context, bridge);
        return bridge.await();
    }

    @Override
    public ModelStreamHandle stream(
            ModelRequest request,
            InvocationContext context,
            ModelStreamObserver observer
    ) {
        context.checkActive();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean terminal = new AtomicBoolean();
        try {
            model.chat(LangChainModelClient.toLangChainRequest(request),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            if (!ensureActive(context, observer, cancelled, terminal)) {
                                return;
                            }
                            if (!cancelled.get() && !terminal.get()) {
                                observer.onContent(partialResponse);
                            }
                        }

                        @Override
                        public void onPartialToolCall(PartialToolCall partialToolCall) {
                            if (!ensureActive(context, observer, cancelled, terminal)) {
                                return;
                            }
                            if (!cancelled.get() && !terminal.get()) {
                                observer.onToolCall(new ToolCallDelta(
                                        partialToolCall.index(),
                                        partialToolCall.id(),
                                        partialToolCall.name(),
                                        partialToolCall.partialArguments()));
                            }
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            if (!ensureActive(context, observer, cancelled, terminal)) {
                                return;
                            }
                            if (terminal.compareAndSet(false, true) && !cancelled.get()) {
                                observer.onComplete(LangChainModelClient.toArbiterResponse(completeResponse));
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (terminal.compareAndSet(false, true) && !cancelled.get()) {
                                observer.onError(toProviderException(error));
                            }
                        }
                    });
        } catch (RuntimeException error) {
            if (terminal.compareAndSet(false, true)) {
                observer.onError(toProviderException(error));
            }
        }
        return () -> {
            if (cancelled.compareAndSet(false, true) && terminal.compareAndSet(false, true)) {
                observer.onError(ProviderException.cancelled("Streaming invocation cancelled"));
            }
        };
    }

    private static ProviderException toProviderException(Throwable error) {
        return LangChainErrors.translate(error);
    }

    private static boolean ensureActive(
            InvocationContext context,
            ModelStreamObserver observer,
            AtomicBoolean cancelled,
            AtomicBoolean terminal
    ) {
        if (cancelled.get() || terminal.get()) {
            return false;
        }
        try {
            context.checkActive();
            return true;
        } catch (ProviderException error) {
            if (terminal.compareAndSet(false, true)) {
                observer.onError(error);
            }
            return false;
        }
    }

    private static final class CompletableBridge implements ModelStreamObserver {
        private final CompletableFuture<ModelResponse> future = new CompletableFuture<>();

        @Override
        public void onContent(String delta) {
        }

        @Override
        public void onToolCall(ToolCallDelta delta) {
        }

        @Override
        public void onComplete(ModelResponse response) {
            future.complete(response);
        }

        @Override
        public void onError(ProviderException error) {
            future.completeExceptionally(error);
        }

        private ModelResponse await() {
            return future.join();
        }
    }
}
