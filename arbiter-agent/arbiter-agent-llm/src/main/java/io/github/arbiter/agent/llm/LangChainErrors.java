package io.github.arbiter.agent.llm;

import io.github.arbiter.agent.spi.error.ProviderErrorKind;
import io.github.arbiter.agent.spi.error.ProviderException;

import java.util.Locale;

final class LangChainErrors {

    private LangChainErrors() {
    }

    static ProviderException translate(Throwable error) {
        if (error instanceof ProviderException providerException) {
            return providerException;
        }
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        ProviderErrorKind kind = ProviderErrorKind.TRANSIENT;
        boolean retryable = true;
        if (message.contains("401") || message.contains("unauthorized")) {
            kind = ProviderErrorKind.AUTHENTICATION;
            retryable = false;
        } else if (message.contains("403") || message.contains("forbidden")) {
            kind = ProviderErrorKind.AUTHORIZATION;
            retryable = false;
        } else if (message.contains("429") || message.contains("rate limit")) {
            kind = ProviderErrorKind.RATE_LIMIT;
        } else if (message.contains("context length") || message.contains("maximum context")) {
            kind = ProviderErrorKind.CONTEXT_LENGTH_EXCEEDED;
            retryable = false;
        } else if (message.contains("timeout") || message.contains("timed out")) {
            kind = ProviderErrorKind.TIMEOUT;
            retryable = false;
        }
        return new ProviderException(kind, "LangChain4j model call failed", retryable, null, error);
    }
}
