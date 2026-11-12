package io.github.arbiter.server.agent.state;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentResult;
import io.github.arbiter.agent.core.EventSink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable control-plane state used by the Console API and the Agent worker.
 * Implementations must make conversation ownership, active-run checks and
 * queue insertion atomic.
 */
public interface AgentStateRepository extends EventSink, AutoCloseable {
    QueuedRun enqueue(
            String owner,
            String scope,
            String requestedConversationId,
            String message,
            Instant now,
            int maxRunsPerMinute,
            long dailyTokenBudget
    );

    Optional<StoredRun> findRun(String runId);

    Optional<StoredRun> claimNextQueued(String workerId, Instant now);

    List<StoredMessage> recentMessages(String conversationId, int limit);

    void complete(StoredRun run, AgentResult result, Instant now);

    void fail(String runId, String failure, Instant now);

    List<AgentEvent> events(String runId);

    void recoverStaleRuns(Instant cutoff);

    void cleanup(Instant now, java.time.Duration runRetention,
                 java.time.Duration conversationRetention);
}
