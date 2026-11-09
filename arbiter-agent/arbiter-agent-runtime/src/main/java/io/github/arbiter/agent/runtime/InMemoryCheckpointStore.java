package io.github.arbiter.agent.runtime;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCheckpointStore implements CheckpointStore {

    private final ConcurrentHashMap<String, AgentCheckpoint> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void save(AgentCheckpoint checkpoint) {
        checkpoints.put(checkpoint.key(), checkpoint);
    }

    @Override
    public Optional<AgentCheckpoint> load(String taskId, String sessionId) {
        return Optional.ofNullable(checkpoints.get(key(taskId, sessionId)));
    }

    @Override
    public void delete(String taskId, String sessionId) {
        checkpoints.remove(key(taskId, sessionId));
    }

    public int size() {
        return checkpoints.size();
    }

    private static String key(String taskId, String sessionId) {
        return taskId.length() + ":" + taskId + sessionId;
    }
}
