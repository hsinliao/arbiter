package io.github.arbiter.server.agent.state;

import io.github.arbiter.agent.core.AgentEvent;
import io.github.arbiter.agent.core.AgentResult;
import io.github.arbiter.agent.core.AgentRunStatus;
import io.github.arbiter.api.crypto.AesTextCipher;
import io.github.arbiter.api.crypto.KeyMaterial;
import io.github.arbiter.api.crypto.KeyRing;
import io.github.arbiter.server.auth.ServerKeyDerivation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed run queue and conversation transcript. The schema is deliberately
 * limited to portable SQL so it works with H2 for local deployments and
 * PostgreSQL for multi-instance production deployments.
 */
public final class JdbcAgentStateRepository implements AgentStateRepository {
    private static final String CIPHERTEXT_PREFIX = "arbiter-agent:v1:";
    private static final String QUEUED = "QUEUED";
    private static final String RUNNING = "RUNNING";
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final KeyRing encryptionKeys;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public JdbcAgentStateRepository(
            String jdbcUrl,
            String username,
            String password,
            KeyRing encryptionKeys
    ) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Agent JDBC URL is required");
        }
        this.jdbcUrl = jdbcUrl;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        if (encryptionKeys == null) {
            throw new IllegalArgumentException("Agent encryption keys are required");
        }
        this.encryptionKeys = encryptionKeys;
        initialize();
    }

    @Override
    public QueuedRun enqueue(
            String owner,
            String scope,
            String requestedConversationId,
            String message,
            Instant now,
            int maxRunsPerMinute,
            long dailyTokenBudget
    ) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                Conversation conversation = requestedConversationId == null
                        ? createConversation(connection, owner, scope, now)
                        : lockConversation(connection, requestedConversationId, owner, scope, now);
                if (activeRunCount(connection, conversation.id()) > 0) {
                    throw new AgentStateException(
                            AgentStateException.Kind.CONVERSATION_BUSY,
                            "Conversation already has an active run.");
                }
                Instant minuteAgo = now.minus(Duration.ofMinutes(1));
                if (runCountSince(connection, owner, minuteAgo) >= maxRunsPerMinute) {
                    throw new AgentStateException(
                            AgentStateException.Kind.RATE_LIMITED,
                            "Assistant request limit reached.");
                }
                Instant dayStart = LocalDate.ofInstant(now, ZoneOffset.UTC)
                        .atStartOfDay().toInstant(ZoneOffset.UTC);
                if (dailyTokensSince(connection, owner, dayStart) >= dailyTokenBudget) {
                    throw new AgentStateException(
                            AgentStateException.Kind.BUDGET_EXCEEDED,
                            "Daily assistant token budget reached.");
                }
                appendMessage(connection, conversation.id(), "USER", message, now);
                String runId = UUID.randomUUID().toString();
                insertRun(connection, runId, conversation, message, now);
                connection.commit();
                return new QueuedRun(findRun(connection, runId).orElseThrow());
            } catch (RuntimeException failure) {
                rollback(connection);
                throw failure;
            } catch (SQLException failure) {
                rollback(connection);
                throw new IllegalStateException("Unable to queue Agent run", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to access Agent state", failure);
        }
    }

    @Override
    public Optional<StoredRun> findRun(String runId) {
        try (Connection connection = connection()) {
            return findRun(connection, runId);
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to load Agent run", failure);
        }
    }

    @Override
    public Optional<StoredRun> claimNextQueued(String workerId, Instant now) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                String runId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT run_id FROM agent_runs
                        WHERE status = ?
                        ORDER BY created_at
                        LIMIT 1 FOR UPDATE
                        """)) {
                    statement.setString(1, QUEUED);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            connection.rollback();
                            return Optional.empty();
                        }
                        runId = rows.getString(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE agent_runs
                        SET status = ?, worker_id = ?, claimed_at = ?, started_at = ?
                        WHERE run_id = ? AND status = ?
                        """)) {
                    statement.setString(1, RUNNING);
                    statement.setString(2, workerId);
                    statement.setTimestamp(3, Timestamp.from(now));
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setString(5, runId);
                    statement.setString(6, QUEUED);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                clearEvents(connection, runId);
                connection.commit();
                return findRun(connection, runId);
            } catch (RuntimeException failure) {
                rollback(connection);
                throw failure;
            } catch (SQLException failure) {
                rollback(connection);
                throw new IllegalStateException("Unable to claim Agent run", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to access Agent queue", failure);
        }
    }

    @Override
    public List<StoredMessage> recentMessages(String conversationId, int limit) {
        List<StoredMessage> messages = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT role, content, created_at
                     FROM agent_messages
                     WHERE conversation_id = ?
                     ORDER BY message_seq DESC
                     LIMIT ?
                     """)) {
            statement.setString(1, conversationId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    messages.add(new StoredMessage(
                            rows.getString(1), decrypt(rows.getString(2)),
                            instant(rows, 3)));
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to load conversation history", failure);
        }
        Collections.reverse(messages);
        return List.copyOf(messages);
    }

    @Override
    public void complete(StoredRun run, AgentResult result, Instant now) {
        String status = switch (result.status()) {
            case SUCCEEDED -> "SUCCEEDED";
            case CANCELLED -> "CANCELLED";
            default -> "FAILED";
        };
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                updateRunResult(connection, run.runId(), status, result, now);
                if (result.status() == AgentRunStatus.SUCCEEDED
                        && result.output() != null && !result.output().isBlank()) {
                    lockConversation(connection, run.conversationId(),
                            run.owner(), run.scope(), now);
                    appendMessage(connection, run.conversationId(),
                            "ASSISTANT", result.output(), now);
                }
                connection.commit();
            } catch (RuntimeException failure) {
                rollback(connection);
                throw failure;
            } catch (SQLException failure) {
                rollback(connection);
                throw new IllegalStateException("Unable to complete Agent run", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to access Agent state", failure);
        }
    }

    @Override
    public void fail(String runId, String failure, Instant now) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE agent_runs
                     SET status = 'FAILED', failure = ?, finished_at = ?
                     WHERE run_id = ?
                     """)) {
            statement.setString(1, encrypt(failure));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, runId);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to fail Agent run", error);
        }
    }

    @Override
    public void emit(AgentEvent event) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_events
                     (run_id, event_seq, event_type, occurred_at, attributes_json)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, event.taskId());
            statement.setLong(2, event.sequence());
            statement.setString(3, event.type().name());
            statement.setTimestamp(4, Timestamp.from(event.occurredAt()));
            statement.setString(5, encrypt(mapper.writeValueAsString(event.attributes())));
            statement.executeUpdate();
        } catch (JacksonException failure) {
            throw new IllegalStateException("Unable to serialize Agent event", failure);
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to persist Agent event", failure);
        }
    }

    @Override
    public List<AgentEvent> events(String runId) {
        List<AgentEvent> events = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT event_seq, event_type, occurred_at, attributes_json
                     FROM agent_events
                     WHERE run_id = ?
                     ORDER BY event_seq
                     """)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    events.add(AgentEvent.builder(
                                    io.github.arbiter.agent.core.AgentEventType
                                            .valueOf(rows.getString(2)))
                            .taskId(runId)
                            .sequence(rows.getLong(1))
                            .occurredAt(instant(rows, 3))
                            .attributes(attributes(decrypt(rows.getString(4))))
                            .build());
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to load Agent events", failure);
        }
        return List.copyOf(events);
    }

    @Override
    public void recoverStaleRuns(Instant cutoff) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE agent_runs
                     SET status = 'QUEUED', worker_id = NULL, claimed_at = NULL
                     WHERE status = 'RUNNING' AND claimed_at < ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to recover stale Agent runs", failure);
        }
    }

    @Override
    public void cleanup(Instant now, Duration runRetention,
                        Duration conversationRetention) {
        Instant runsBefore = now.minus(runRetention);
        Instant conversationsBefore = now.minus(conversationRetention);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM agent_events
                        WHERE run_id IN (
                            SELECT run_id FROM agent_runs
                            WHERE status NOT IN ('QUEUED', 'RUNNING')
                              AND created_at < ?
                        )
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(runsBefore));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM agent_runs
                        WHERE status NOT IN ('QUEUED', 'RUNNING') AND created_at < ?
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(runsBefore));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM agent_messages
                        WHERE conversation_id IN (
                            SELECT c.id FROM agent_conversations c
                            WHERE c.last_used_at < ?
                              AND NOT EXISTS (
                                  SELECT 1 FROM agent_runs r
                                  WHERE r.conversation_id = c.id
                                    AND r.status IN ('QUEUED', 'RUNNING')
                              )
                        )
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(conversationsBefore));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM agent_conversations c
                        WHERE c.last_used_at < ?
                          AND NOT EXISTS (
                              SELECT 1 FROM agent_runs r
                              WHERE r.conversation_id = c.id
                                AND r.status IN ('QUEUED', 'RUNNING')
                          )
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(conversationsBefore));
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to clean Agent state", failure);
        }
    }

    @Override
    public void close() {
        // Connections are short-lived; no pool is owned by this repository.
    }

    private void initialize() {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS agent_conversations (
                        id VARCHAR(64) PRIMARY KEY,
                        owner VARCHAR(100) NOT NULL,
                        scope VARCHAR(50) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        last_used_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS agent_messages (
                        conversation_id VARCHAR(64) NOT NULL,
                        message_seq BIGINT NOT NULL,
                        role VARCHAR(20) NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (conversation_id, message_seq)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS agent_runs (
                        run_id VARCHAR(64) PRIMARY KEY,
                        conversation_id VARCHAR(64) NOT NULL,
                        owner VARCHAR(100) NOT NULL,
                        scope VARCHAR(50) NOT NULL,
                        prompt TEXT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        output TEXT,
                        failure TEXT,
                        created_at TIMESTAMP NOT NULL,
                        started_at TIMESTAMP,
                        finished_at TIMESTAMP,
                        iterations INTEGER DEFAULT 0,
                        input_tokens INTEGER DEFAULT 0,
                        output_tokens INTEGER DEFAULT 0,
                        cost DECIMAL(18, 8) DEFAULT 0,
                        worker_id VARCHAR(100),
                        claimed_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS agent_events (
                        run_id VARCHAR(64) NOT NULL,
                        event_seq BIGINT NOT NULL,
                        event_type VARCHAR(50) NOT NULL,
                        occurred_at TIMESTAMP NOT NULL,
                        attributes_json TEXT NOT NULL,
                        PRIMARY KEY (run_id, event_seq)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_agent_runs_queue "
                    + "ON agent_runs(status, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_agent_runs_owner "
                    + "ON agent_runs(owner, created_at)");
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to initialize Agent schema", failure);
        }
    }

    private Conversation createConversation(
            Connection connection, String owner, String scope, Instant now
    ) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_conversations
                (id, owner, scope, created_at, last_used_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, owner);
            statement.setString(3, scope);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
        return new Conversation(id, owner);
    }

    private Conversation lockConversation(
            Connection connection, String id, String owner, String scope, Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT owner FROM agent_conversations WHERE id = ? FOR UPDATE
                """)) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !owner.equals(rows.getString(1))) {
                    throw new AgentStateException(
                            AgentStateException.Kind.CONVERSATION_INVALID,
                            "Conversation is unavailable.");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE agent_conversations
                SET scope = ?, last_used_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, scope);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, id);
            statement.executeUpdate();
        }
        return new Conversation(id, owner);
    }

    private int activeRunCount(Connection connection, String conversationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM agent_runs
                WHERE conversation_id = ? AND status IN ('QUEUED', 'RUNNING')
                """)) {
            statement.setString(1, conversationId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int runCountSince(Connection connection, String owner, Instant since)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM agent_runs
                WHERE owner = ? AND created_at >= ?
                """)) {
            statement.setString(1, owner);
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private long dailyTokensSince(Connection connection, String owner, Instant since)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(input_tokens + output_tokens), 0)
                FROM agent_runs WHERE owner = ? AND created_at >= ?
                """)) {
            statement.setString(1, owner);
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private void insertRun(
            Connection connection,
            String runId,
            Conversation conversation,
            String message,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_runs
                (run_id, conversation_id, owner, scope, prompt, status, created_at,
                 iterations, input_tokens, output_tokens, cost)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0)
                """)) {
            statement.setString(1, runId);
            statement.setString(2, conversation.id());
            statement.setString(3, conversation.owner());
            statement.setString(4, currentScope(connection, conversation.id()));
            statement.setString(5, encrypt(message));
            statement.setString(6, QUEUED);
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private String currentScope(Connection connection, String conversationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT scope FROM agent_conversations WHERE id = ?")) {
            statement.setString(1, conversationId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private void appendMessage(
            Connection connection, String conversationId,
            String role, String content, Instant now
    ) throws SQLException {
        long sequence;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(MAX(message_seq), 0) + 1
                FROM agent_messages WHERE conversation_id = ?
                """)) {
            statement.setString(1, conversationId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                sequence = rows.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_messages
                (conversation_id, message_seq, role, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, conversationId);
            statement.setLong(2, sequence);
            statement.setString(3, role);
            statement.setString(4, encrypt(content));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void updateRunResult(
            Connection connection, String runId, String status,
            AgentResult result, Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE agent_runs
                SET status = ?, output = ?, failure = ?, finished_at = ?,
                    iterations = ?, input_tokens = ?, output_tokens = ?, cost = ?,
                    worker_id = NULL, claimed_at = NULL
                WHERE run_id = ?
                """)) {
            statement.setString(1, status);
            statement.setString(2, encrypt(result.output()));
            statement.setString(3, encrypt(result.failure()));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setInt(5, result.iterations());
            statement.setInt(6, result.usage() == null || result.usage().inputTokens() == null
                    ? 0 : result.usage().inputTokens());
            statement.setInt(7, result.usage() == null || result.usage().outputTokens() == null
                    ? 0 : result.usage().outputTokens());
            statement.setBigDecimal(8, result.cost() == null
                    ? BigDecimal.ZERO : result.cost());
            statement.setString(9, runId);
            statement.executeUpdate();
        }
    }

    private Optional<StoredRun> findRun(Connection connection, String runId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT run_id, conversation_id, owner, scope, prompt, status,
                       output, failure, created_at, started_at, finished_at,
                       iterations, input_tokens, output_tokens, cost
                FROM agent_runs WHERE run_id = ?
                """)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredRun(
                        rows.getString(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), decrypt(rows.getString(5)),
                        rows.getString(6), decrypt(rows.getString(7)),
                        decrypt(rows.getString(8)), instant(rows, 9),
                        instant(rows, 10), instant(rows, 11), rows.getInt(12),
                        rows.getInt(13), rows.getInt(14),
                        rows.getBigDecimal(15) == null
                                ? BigDecimal.ZERO : rows.getBigDecimal(15)));
            }
        }
    }

    private void clearEvents(Connection connection, String runId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM agent_events WHERE run_id = ?")) {
            statement.setString(1, runId);
            statement.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attributes(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (JacksonException failure) {
            return Map.of("parseError", "invalid persisted event attributes");
        }
    }

    private String encrypt(String plaintext) {
        String value = plaintext == null ? "" : plaintext;
        KeyMaterial key = encryptionKeys.active();
        return CIPHERTEXT_PREFIX + key.id() + ":"
                + AesTextCipher.encrypt(
                value, ServerKeyDerivation.agentState(key.key()));
    }

    private String decrypt(String stored) {
        if (stored == null || !stored.startsWith(CIPHERTEXT_PREFIX)) {
            return stored == null ? "" : stored;
        }
        String remainder = stored.substring(CIPHERTEXT_PREFIX.length());
        int separator = remainder.indexOf(':');
        if (separator <= 0 || separator == remainder.length() - 1) {
            throw new IllegalStateException("Invalid encrypted Agent state");
        }
        String keyId = remainder.substring(0, separator);
        KeyMaterial key = encryptionKeys.find(keyId).orElseThrow(() ->
                new IllegalStateException("Agent state encryption key is unavailable"));
        return AesTextCipher.decrypt(
                remainder.substring(separator + 1),
                ServerKeyDerivation.agentState(key.key()));
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static Instant instant(ResultSet rows, int column) throws SQLException {
        Timestamp timestamp = rows.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    private record Conversation(String id, String owner) {
    }
}
