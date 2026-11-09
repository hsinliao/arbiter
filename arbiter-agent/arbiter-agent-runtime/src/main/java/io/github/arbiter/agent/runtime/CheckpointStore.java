package io.github.arbiter.agent.runtime;

import java.util.Optional;

public interface CheckpointStore {

    void save(AgentCheckpoint checkpoint);

    Optional<AgentCheckpoint> load(String taskId, String sessionId);

    default void delete(String taskId, String sessionId) {
    }
}
