package io.github.arbiter.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuration for the optional read-only Console assistant.
 *
 * <p>The API key belongs in {@code conf/server.properties}; it is never
 * exposed by the console bootstrap endpoint or returned by the Agent API.</p>
 */
@ConfigurationProperties(prefix = "arbiter.agent")
public final class AgentProperties {
    private boolean enabled;
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "";
    private String systemPrompt = """
            You are Arbiter Assistant inside the Arbiter Console.
            Answer in the user's language.
            Use read-only tools when live service information is needed.
            Never invent evidence. Clearly separate observations, inferences, and suggested actions.
            You cannot change system state. For any state-changing request, produce a short plan and
            tell the user that an administrator must confirm it in the Console.
            """;
    private int maxIterations = 6;
    private int maxTotalTokens = 20_000;
    private BigDecimal maxCost = BigDecimal.ZERO;
    private Duration timeout = Duration.ofSeconds(60);
    private Set<String> toolGroups = new LinkedHashSet<>(
            Set.of("system", "reader", "docs", "admin"));
    private Double temperature = 0.2;
    private Integer maxOutputTokens = 1_200;
    private int maxConcurrentRuns = 2;
    private int maxRunsPerMinute = 12;
    private long dailyTokenBudget = 100_000;
    private int queueCapacity = 16;
    private int historyMessages = 20;
    private int maxRequestChars = 8_000;
    private Duration runRetention = Duration.ofHours(1);
    private Duration conversationRetention = Duration.ofHours(8);
    private String storageJdbcUrl = "jdbc:h2:file:${arbiter.home}/data/agent"
            + ";AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private String storageUsername = "sa";
    private String storagePassword = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getMaxTotalTokens() {
        return maxTotalTokens;
    }

    public void setMaxTotalTokens(int maxTotalTokens) {
        this.maxTotalTokens = maxTotalTokens;
    }

    public BigDecimal getMaxCost() {
        return maxCost;
    }

    public void setMaxCost(BigDecimal maxCost) {
        this.maxCost = maxCost;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Set<String> getToolGroups() {
        return toolGroups;
    }

    public void setToolGroups(Set<String> toolGroups) {
        this.toolGroups = toolGroups;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public void setMaxConcurrentRuns(int maxConcurrentRuns) {
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    public int getMaxRunsPerMinute() {
        return maxRunsPerMinute;
    }

    public void setMaxRunsPerMinute(int maxRunsPerMinute) {
        this.maxRunsPerMinute = maxRunsPerMinute;
    }

    public long getDailyTokenBudget() {
        return dailyTokenBudget;
    }

    public void setDailyTokenBudget(long dailyTokenBudget) {
        this.dailyTokenBudget = dailyTokenBudget;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getHistoryMessages() {
        return historyMessages;
    }

    public void setHistoryMessages(int historyMessages) {
        this.historyMessages = historyMessages;
    }

    public int getMaxRequestChars() {
        return maxRequestChars;
    }

    public void setMaxRequestChars(int maxRequestChars) {
        this.maxRequestChars = maxRequestChars;
    }

    public Duration getRunRetention() {
        return runRetention;
    }

    public void setRunRetention(Duration runRetention) {
        this.runRetention = runRetention;
    }

    public Duration getConversationRetention() {
        return conversationRetention;
    }

    public void setConversationRetention(Duration conversationRetention) {
        this.conversationRetention = conversationRetention;
    }

    public String getStorageJdbcUrl() {
        return storageJdbcUrl;
    }

    public void setStorageJdbcUrl(String storageJdbcUrl) {
        this.storageJdbcUrl = storageJdbcUrl;
    }

    public String getStorageUsername() {
        return storageUsername;
    }

    public void setStorageUsername(String storageUsername) {
        this.storageUsername = storageUsername;
    }

    public String getStoragePassword() {
        return storagePassword;
    }

    public void setStoragePassword(String storagePassword) {
        this.storagePassword = storagePassword;
    }

    public void validate() {
        if (!enabled) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "arbiter.agent.api-key is required when the assistant is enabled");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                    "arbiter.agent.model is required when the assistant is enabled");
        }
        URI endpoint;
        try {
            endpoint = URI.create(baseUrl);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("arbiter.agent.base-url is not a valid URI");
        }
        if (endpoint.getScheme() == null
                || !(endpoint.getScheme().equals("http")
                || endpoint.getScheme().equals("https"))
                || endpoint.getHost() == null) {
            throw new IllegalArgumentException(
                    "arbiter.agent.base-url must be an absolute http or https URL");
        }
        requirePositive(maxIterations, "arbiter.agent.max-iterations");
        requirePositive(maxTotalTokens, "arbiter.agent.max-total-tokens");
        requirePositive(maxOutputTokens, "arbiter.agent.max-output-tokens");
        requirePositive(maxConcurrentRuns, "arbiter.agent.max-concurrent-runs");
        requirePositive(maxRunsPerMinute, "arbiter.agent.max-runs-per-minute");
        requirePositive(dailyTokenBudget, "arbiter.agent.daily-token-budget");
        requirePositive(queueCapacity, "arbiter.agent.queue-capacity");
        requirePositive(historyMessages, "arbiter.agent.history-messages");
        requirePositive(maxRequestChars, "arbiter.agent.max-request-chars");
        requirePositive(timeout, "arbiter.agent.timeout");
        requirePositive(runRetention, "arbiter.agent.run-retention");
        requirePositive(conversationRetention, "arbiter.agent.conversation-retention");
        if (maxCost == null || maxCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "arbiter.agent.max-cost must not be negative");
        }
        if (temperature == null || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException(
                    "arbiter.agent.temperature must be between 0 and 2");
        }
        if (toolGroups == null) {
            toolGroups = new LinkedHashSet<>();
        }
        if (storageJdbcUrl == null || storageJdbcUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "arbiter.agent.storage-jdbc-url is required");
        }
    }

    private static void requirePositive(int value, String key) {
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
    }

    private static void requirePositive(Integer value, String key) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(key + " must be positive");
        }
    }

    private static void requirePositive(long value, String key) {
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
    }
}
