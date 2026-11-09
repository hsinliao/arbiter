package io.github.arbiter.agent.spi.model;

public record Usage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer cacheReadTokens,
        Integer reasoningTokens
) {
    public Usage {
        if (totalTokens == null && (inputTokens != null || outputTokens != null)) {
            totalTokens = (inputTokens == null ? 0 : inputTokens)
                    + (outputTokens == null ? 0 : outputTokens);
        }
    }

    public static UsageBuilder builder() {
        return new UsageBuilder();
    }

    public static class UsageBuilder {
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private Integer cacheReadTokens;
        private Integer reasoningTokens;

        public UsageBuilder inputTokens(Integer inputTokens) { this.inputTokens = inputTokens; return this; }
        public UsageBuilder outputTokens(Integer outputTokens) { this.outputTokens = outputTokens; return this; }
        public UsageBuilder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        public UsageBuilder cacheReadTokens(Integer cacheReadTokens) {
            this.cacheReadTokens = cacheReadTokens;
            return this;
        }
        public UsageBuilder reasoningTokens(Integer reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
            return this;
        }
        public Usage build() {
            return new Usage(inputTokens, outputTokens, totalTokens, cacheReadTokens, reasoningTokens);
        }
    }

}
