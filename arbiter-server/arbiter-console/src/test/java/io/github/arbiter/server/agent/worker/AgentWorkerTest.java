package io.github.arbiter.server.agent.worker;

import io.github.arbiter.server.config.AgentProperties;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkerTest {

    @Test
    void adminToolsAreOnlyEnabledForAdminScope() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxConcurrentRuns(1);
        properties.setToolGroups(java.util.Set.of("system", "reader", "docs", "admin"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AgentWorker worker = new AgentWorker(
                    null, null, properties, executor, java.time.Clock.systemUTC());
            assertTrue(worker.toolGroups("admin").contains("admin"));
            assertFalse(worker.toolGroups("reader").contains("admin"));
            assertTrue(worker.toolGroups("reader").contains("reader"));
            assertTrue(worker.toolGroups("reader").contains("docs"));
        } finally {
            executor.shutdownNow();
        }
    }
}
