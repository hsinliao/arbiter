package io.github.arbiter.server.service;

import io.github.arbiter.agent.llm.ScriptedModelClient;
import io.github.arbiter.agent.runtime.RuntimeAgent;
import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.dto.AgentRunRequest;
import io.github.arbiter.api.dto.AgentRunResponse;
import io.github.arbiter.api.crypto.KeyRing;
import io.github.arbiter.server.agent.state.JdbcAgentStateRepository;
import io.github.arbiter.server.agent.worker.AgentWorker;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.config.AgentProperties;
import io.github.arbiter.server.error.ApiException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private JdbcAgentStateRepository repository;
    private RuntimeAgent runtimeAgent;
    private AgentWorker worker;

    @AfterEach
    void closeRuntime() {
        if (worker != null) {
            worker.stop();
        }
        if (runtimeAgent != null) {
            runtimeAgent.close();
        }
        if (repository != null) {
            repository.close();
        }
        executor.shutdownNow();
    }

    @Test
    void runsTurnsAsynchronouslyAndReusesOrderedHistory() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueueText("service is healthy")
                .enqueueText("no new errors");
        AgentProperties properties = enabledProperties();
        repository = repository();
        runtimeAgent = RuntimeAgent.builder()
                .modelClient(model)
                .eventSink(repository)
                .build();
        worker = new AgentWorker(
                repository, runtimeAgent, properties, executor, Clock.systemUTC());
        worker.start();
        AgentService service = new AgentService(
                properties, repository, Clock.systemUTC());
        TokenClaims alice = claims("alice", "reader");

        AgentRunResponse first = service.submit(
                alice, new AgentRunRequest("is the service healthy?", null));
        first = awaitFinished(service, alice, first);

        assertEquals("SUCCEEDED", first.status());
        assertEquals("service is healthy", first.output());
        assertTrue(first.events().stream()
                .anyMatch(event -> "RUN_COMPLETED".equals(event.type())));

        AgentRunResponse second = service.submit(
                alice, new AgentRunRequest(
                        "were there any new errors?", first.conversationId()));
        second = awaitFinished(service, alice, second);

        assertEquals("SUCCEEDED", second.status());
        assertEquals("no new errors", second.output());
        assertEquals(first.conversationId(), second.conversationId());
        assertNotEquals(first.runId(), second.runId());
        var secondMessages = model.requests().get(1).messages();
        String secondPrompt = secondMessages.get(secondMessages.size() - 1).content();
        assertTrue(secondPrompt.contains("is the service healthy?"));
        assertTrue(secondPrompt.contains("service is healthy"));
        assertTrue(secondPrompt.contains("were there any new errors?"));
    }

    @Test
    void sameConversationCannotHaveTwoActiveRuns() {
        AgentProperties properties = enabledProperties();
        repository = repository();
        AgentService service = new AgentService(
                properties, repository, Clock.systemUTC());
        TokenClaims alice = claims("alice", "reader");

        AgentRunResponse first = service.submit(
                alice, new AgentRunRequest("first", null));
        ApiException conflict = assertThrows(ApiException.class,
                () -> service.submit(alice, new AgentRunRequest(
                        "second", first.conversationId())));

        assertEquals(409, conflict.status());
        assertEquals(ApiErrorCodes.CONFLICT, conflict.code());
    }

    @Test
    void hidesRunsAndConversationsFromOtherUsers() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient().enqueueText("ok");
        AgentProperties properties = enabledProperties();
        repository = repository();
        runtimeAgent = RuntimeAgent.builder()
                .modelClient(model)
                .eventSink(repository)
                .build();
        worker = new AgentWorker(
                repository, runtimeAgent, properties, executor, Clock.systemUTC());
        worker.start();
        AgentService service = new AgentService(
                properties, repository, Clock.systemUTC());
        TokenClaims alice = claims("alice", "reader");
        TokenClaims bob = claims("bob", "reader");

        AgentRunResponse run = awaitFinished(service, alice,
                service.submit(alice, new AgentRunRequest("status", null)));

        ApiException hidden = assertThrows(ApiException.class,
                () -> service.get(bob, run.runId()));
        assertEquals(404, hidden.status());
        assertEquals(ApiErrorCodes.NOT_FOUND, hidden.code());

        ApiException conversation = assertThrows(ApiException.class,
                () -> service.submit(bob,
                        new AgentRunRequest("continue", run.conversationId())));
        assertEquals(400, conversation.status());
    }

    @Test
    void reportsUnavailableWhenDisabled() {
        AgentProperties properties = new AgentProperties();
        AgentService service = new AgentService(
                properties, null, Clock.systemUTC());

        assertFalse(service.capabilities().enabled());
        ApiException unavailable = assertThrows(ApiException.class,
                () -> service.submit(
                        claims("alice", "reader"),
                        new AgentRunRequest("hello", null)));
        assertEquals(503, unavailable.status());
        assertEquals(ApiErrorCodes.AGENT_UNAVAILABLE, unavailable.code());
    }

    private static AgentRunResponse awaitFinished(
            AgentService service, TokenClaims claims, AgentRunResponse initial)
            throws Exception {
        AgentRunResponse current = initial;
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while ("QUEUED".equals(current.status()) || "RUNNING".equals(current.status())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Agent run did not finish");
            }
            Thread.sleep(20);
            current = service.get(claims, current.runId());
        }
        return current;
    }

    private static JdbcAgentStateRepository repository() {
        return new JdbcAgentStateRepository(
                "jdbc:h2:mem:agent-" + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa", "",
                new KeyRing("test", java.util.Map.of("test", new byte[32])));
    }

    private static AgentProperties enabledProperties() {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setMaxConcurrentRuns(1);
        properties.setMaxRunsPerMinute(20);
        properties.setDailyTokenBudget(100_000);
        properties.setHistoryMessages(20);
        properties.setQueueCapacity(2);
        properties.validate();
        return properties;
    }

    private static TokenClaims claims(String subject, String scope) {
        return new TokenClaims(subject, scope, Instant.now(),
                Instant.now().plusSeconds(60));
    }
}
