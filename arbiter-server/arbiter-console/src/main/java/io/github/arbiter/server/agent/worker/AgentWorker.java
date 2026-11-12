package io.github.arbiter.server.agent.worker;

import io.github.arbiter.agent.core.AgentConfig;
import io.github.arbiter.agent.core.AgentContext;
import io.github.arbiter.agent.core.AgentResult;
import io.github.arbiter.agent.core.AgentTask;
import io.github.arbiter.agent.runtime.RuntimeAgent;
import io.github.arbiter.server.agent.state.AgentStateRepository;
import io.github.arbiter.server.agent.state.StoredMessage;
import io.github.arbiter.server.agent.state.StoredRun;
import io.github.arbiter.server.config.AgentProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls durable queued runs and executes them in the bounded Agent executor.
 * Console API code never executes a model or tool directly.
 */
public final class AgentWorker implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AgentWorker.class);
    private final AgentStateRepository repository;
    private final RuntimeAgent runtimeAgent;
    private final AgentProperties properties;
    private final ExecutorService executor;
    private final Clock clock;
    private final String workerId = UUID.randomUUID().toString();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Semaphore slots;
    private final ConcurrentHashMap<String, AgentContext> activeContexts =
            new ConcurrentHashMap<>();
    private Thread poller;

    public AgentWorker(
            AgentStateRepository repository,
            RuntimeAgent runtimeAgent,
            AgentProperties properties,
            ExecutorService executor,
            Clock clock
    ) {
        this.repository = repository;
        this.runtimeAgent = runtimeAgent;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
        this.slots = new Semaphore(properties.getMaxConcurrentRuns());
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Instant staleBefore = clock.instant().minus(properties.getTimeout().multipliedBy(2));
        repository.recoverStaleRuns(staleBefore);
        poller = new Thread(this::pollLoop, "arbiter-agent-worker");
        poller.setDaemon(true);
        poller.start();
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (poller != null) {
            poller.interrupt();
        }
        activeContexts.values().forEach(AgentContext::cancel);
        activeContexts.clear();
    }

    @Override
    public void close() {
        stop();
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                if (!slots.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                    continue;
                }
                boolean submitted = false;
                try {
                    Instant now = clock.instant();
                    repository.cleanup(
                            now, properties.getRunRetention(),
                            properties.getConversationRetention());
                    var queued = repository.claimNextQueued(workerId, now);
                    if (queued.isPresent()) {
                        StoredRun run = queued.get();
                        executor.submit(() -> execute(run));
                        submitted = true;
                    }
                } finally {
                    if (!submitted) {
                        slots.release();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException failure) {
                LOG.error("Agent worker poll failed", failure);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void execute(StoredRun run) {
        Instant startedAt = clock.instant();
        AgentContext context = new AgentContext(
                run.runId(), run.conversationId(),
                startedAt.plus(properties.getTimeout()),
                Map.of("subject", run.owner(), "scope", run.scope()));
        activeContexts.put(run.runId(), context);
        try {
            AgentTask task = AgentTask.of(run.runId(), conversationInput(run));
            AgentResult result = runtimeAgent.run(task, context, agentConfig(run.scope()));
            repository.complete(run, result, clock.instant());
        } catch (RuntimeException failure) {
            LOG.warn("Agent run {} failed", run.runId(), failure);
            repository.fail(run.runId(), "The assistant run failed.", clock.instant());
        } finally {
            activeContexts.remove(run.runId());
            slots.release();
        }
    }

    private String conversationInput(StoredRun run) {
        List<StoredMessage> messages = repository.recentMessages(
                run.conversationId(), properties.getHistoryMessages());
        StringBuilder input = new StringBuilder();
        input.append("The following is the ordered conversation transcript. ")
                .append("Respond to the final USER message.\n\n");
        for (StoredMessage message : messages) {
            input.append(message.role()).append(":\n")
                    .append(message.content()).append("\n\n");
        }
        return input.toString().strip();
    }

    private AgentConfig agentConfig(String scope) {
        return AgentConfig.builder()
                .name("arbiter-console-assistant")
                .systemPrompt(properties.getSystemPrompt())
                .modelCode(properties.getModel())
                .maxIterations(properties.getMaxIterations())
                .maxTotalTokens(properties.getMaxTotalTokens())
                .maxCost(properties.getMaxCost())
                .timeout(properties.getTimeout())
                .toolGroups(toolGroups(scope))
                .temperature(properties.getTemperature())
                .maxOutputTokens(properties.getMaxOutputTokens())
                .build();
    }

    Set<String> toolGroups(String scope) {
        Set<String> groups = new LinkedHashSet<>(properties.getToolGroups());
        groups.add("system");
        groups.add("reader");
        if ("admin".equals(scope)) {
            groups.add("admin");
        } else {
            groups.remove("admin");
        }
        return Set.copyOf(groups);
    }
}
