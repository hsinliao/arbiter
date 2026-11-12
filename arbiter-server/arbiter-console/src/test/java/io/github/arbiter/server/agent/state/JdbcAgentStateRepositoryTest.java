package io.github.arbiter.server.agent.state;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentEventType;
import io.github.arbiter.agent.core.AgentResult;
import io.github.arbiter.agent.core.AgentRunStatus;
import io.github.arbiter.agent.core.AgentState;
import io.github.arbiter.agent.spi.model.Usage;
import io.github.arbiter.api.crypto.KeyRing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAgentStateRepositoryTest {

    @Test
    void persistsQueueConversationAndEventsAcrossRepositoryInstances() {
        String url = memoryUrl();
        JdbcAgentStateRepository first = new JdbcAgentStateRepository(
                url, "sa", "", keys());
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        StoredRun queued = first.enqueue(
                "alice", "reader", null, "check status", now, 20, 100_000).run();
        assertEquals("QUEUED", queued.status());
        assertEquals("alice", queued.owner());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.prepareStatement(
                     "SELECT prompt FROM agent_runs WHERE run_id = ?")) {
            statement.setString(1, queued.runId());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertTrue(rows.getString(1).startsWith("arbiter-agent:v1:"));
                assertFalse(rows.getString(1).contains("check status"));
            }
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }

        first.close();
        JdbcAgentStateRepository reopened = new JdbcAgentStateRepository(
                url, "sa", "", keys());
        StoredRun persisted = reopened.findRun(queued.runId()).orElseThrow();
        assertEquals(queued.conversationId(), persisted.conversationId());
        assertEquals("check status", persisted.prompt());

        StoredRun claimed = reopened.claimNextQueued(
                "worker-1", now.plusSeconds(1)).orElseThrow();
        assertEquals("RUNNING", claimed.status());

        reopened.emit(AgentEvent.builder(AgentEventType.RUN_STARTED)
                .taskId(claimed.runId())
                .sequence(1)
                .occurredAt(now.plusSeconds(2))
                .attributes(Map.of("iteration", 0))
                .build());
        assertEquals(1, reopened.events(claimed.runId()).size());
        assertEquals(1, reopened.recentMessages(
                claimed.conversationId(), 10).size());
        reopened.close();
    }

    @Test
    void rejectsAConcurrentRunInTheSameConversation() {
        JdbcAgentStateRepository repository = new JdbcAgentStateRepository(
                memoryUrl(), "sa", "", keys());
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        StoredRun first = repository.enqueue(
                "alice", "reader", null, "first", now, 20, 100_000).run();

        AgentStateException busy = assertThrows(AgentStateException.class,
                () -> repository.enqueue(
                        "alice", "reader", first.conversationId(),
                        "second", now.plusSeconds(1), 20, 100_000));
        assertEquals(AgentStateException.Kind.CONVERSATION_BUSY, busy.kind());
    }

    @Test
    void enforcesPerMinuteRateAndDailyTokenBudget() {
        JdbcAgentStateRepository repository = new JdbcAgentStateRepository(
                memoryUrl(), "sa", "", keys());
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        repository.enqueue("alice", "reader", null, "one", now, 1, 100_000);

        AgentStateException rate = assertThrows(AgentStateException.class,
                () -> repository.enqueue(
                        "alice", "reader", null, "two", now.plusSeconds(1), 1, 100_000));
        assertEquals(AgentStateException.Kind.RATE_LIMITED, rate.kind());

        JdbcAgentStateRepository budgetRepository = new JdbcAgentStateRepository(
                memoryUrl(), "sa", "", keys());
        StoredRun spentRun = budgetRepository.enqueue(
                "bob", "reader", null, "account usage",
                now, 20, 100_000).run();
        StoredRun claimed = budgetRepository.claimNextQueued(
                "worker", now.plusSeconds(1)).orElseThrow();
        budgetRepository.complete(claimed, new AgentResult(
                spentRun.runId(), spentRun.conversationId(),
                AgentRunStatus.SUCCEEDED, AgentState.COMPLETED,
                "done", 1,
                Usage.builder().inputTokens(1).outputTokens(0).build(),
                BigDecimal.ZERO, "", now, now.plusSeconds(1), Map.of()),
                now.plusSeconds(1));
        AgentStateException budget = assertThrows(AgentStateException.class,
                () -> budgetRepository.enqueue(
                        "bob", "reader", null, "blocked",
                        now.plusSeconds(2), 20, 1));
        assertEquals(AgentStateException.Kind.BUDGET_EXCEEDED, budget.kind());
    }

    private static String memoryUrl() {
        return "jdbc:h2:mem:agent-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    }

    private static KeyRing keys() {
        return new KeyRing("test", Map.of("test", new byte[32]));
    }
}
