package io.github.arbiter.agent.spi.model;

public record SamplingOptions(Double temperature, Double topP, Integer maxTokens) {

    public static SamplingOptions empty() {
        return new SamplingOptions(null, null, null);
    }
}
