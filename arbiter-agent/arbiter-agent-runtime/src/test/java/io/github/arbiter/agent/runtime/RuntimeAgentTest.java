package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.core.AgentConfig;
import io.github.arbiter.agent.core.AgentContext;
import io.github.arbiter.agent.core.AgentEventType;
import io.github.arbiter.agent.core.AgentResult;
import io.github.arbiter.agent.core.AgentRunStatus;
import io.github.arbiter.agent.core.AgentState;
import io.github.arbiter.agent.core.AgentTask;
import io.github.arbiter.agent.llm.ScriptedModelClient;
import io.github.arbiter.agent.memory.TieredMemory;
import io.github.arbiter.agent.observability.RecordingEventStore;
import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.lifecycle.ProviderConfig;
import io.github.arbiter.agent.spi.lifecycle.ProviderLifecycle;
import io.github.arbiter.agent.spi.llm.ModelClient;
import io.github.arbiter.agent.spi.model.MessageRole;
import io.github.arbiter.agent.spi.model.ModelMessage;
import io.github.arbiter.agent.spi.model.ModelRequest;
import io.github.arbiter.agent.spi.model.ModelResponse;
import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.model.ToolDefinition;
import io.github.arbiter.agent.spi.model.Usage;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolResult;
import io.github.arbiter.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAgentTest {

    @Test
    void executesReActLoopAndSavesEvidence() throws Exception {
        EchoTool tool = new EchoTool();
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool);
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueue(ModelResponse.builder()
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-1")
                                .name("echo")
                                .arguments(Map.of("value", "hello"))
                                .build()))
                        .usage(Usage.builder().inputTokens(3).outputTokens(2).build())
                        .build())
                .enqueue(ModelResponse.builder()
                        .content("tool said hello")
                        .usage(Usage.builder().inputTokens(5).outputTokens(3).build())
                        .build());
        RecordingEventStore events = new RecordingEventStore();
        AgentTask task = AgentTask.of("task-1", "use the echo tool");
        AgentContext context = context(task, "session-1");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .toolRegistry(tools)
                .memory(new TieredMemory())
                .eventSink(events)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult result = agent.run(
                    task, context, AgentConfig.builder()
                            .systemPrompt("Be concise")
                            .toolGroups(Set.of("test"))
                            .maxIterations(4)
                            .build());

            assertEquals(AgentRunStatus.SUCCEEDED, result.status());
            assertEquals("tool said hello", result.output());
            assertEquals(1, tool.calls.get());
            assertEquals(2, model.requests().size());
            ModelMessage toolMessage = model.requests().get(1).messages().stream()
                    .filter(message -> message.role() == MessageRole.TOOL)
                    .findFirst()
                    .orElseThrow();
            assertEquals("call-1", toolMessage.toolCallId());
            assertEquals("echo:hello", toolMessage.content());
            assertTrue(events.events().stream()
                    .anyMatch(event -> event.type() == AgentEventType.TOOL_RESULT));
            assertTrue(events.events().stream()
                    .anyMatch(event -> event.type() == AgentEventType.RUN_COMPLETED));
        }
    }

    @Test
    void resumesFailedCheckpoint() throws Exception {
        FlakyModelClient model = new FlakyModelClient();
        AgentTask task = AgentTask.of("resume-1", "recover");
        AgentContext context = context(task, "resume-session");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult first = agent.run(task, context, AgentConfig.builder().maxIterations(2).build());
            assertEquals(AgentRunStatus.FAILED, first.status());
            assertTrue(agent.checkpoint(task.id(), context.getSessionId()).isPresent());

            AgentResult second = agent.run(task, context, AgentConfig.builder().maxIterations(2).build());
            assertEquals(AgentRunStatus.SUCCEEDED, second.status());
            assertEquals("recovered", second.output());
            assertEquals(2, model.calls.get());
        }
    }

    @Test
    void resumesAfterCompletedToolBatchAndRestoresState() throws Exception {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new StatefulEchoTool());
        InterruptedToolModelClient model = new InterruptedToolModelClient();
        AgentTask task = AgentTask.of("resume-tool-1", "echo this");
        AgentConfig config = AgentConfig.builder()
                .toolGroups(Set.of("test"))
                .maxIterations(3)
                .build();

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .toolRegistry(tools)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentContext firstContext = context(task, "resume-tool-session");
            AgentResult first = agent.run(task, firstContext, config);
            assertEquals(AgentRunStatus.FAILED, first.status());
            assertEquals(42, agent.checkpoint(task.id(), firstContext.getSessionId())
                    .orElseThrow().variables().get("progress"));

            AgentContext resumedContext = context(task, "resume-tool-session");
            AgentResult second = agent.run(task, resumedContext, config);
            assertEquals(AgentRunStatus.SUCCEEDED, second.status());
            assertEquals("finished after resume", second.output());
            assertEquals(42, resumedContext.get("progress"));
            assertTrue(model.requests.get(2).messages().stream()
                    .anyMatch(message -> message.role() == MessageRole.TOOL));
        }
    }

    @Test
    void failsWhenTokenBudgetIsExceeded() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient().enqueue(ModelResponse.builder()
                .content("too large")
                .usage(Usage.builder().inputTokens(10).outputTokens(10).build())
                .build());
        RecordingEventStore events = new RecordingEventStore();
        AgentTask task = AgentTask.of("budget-1", "budget");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .eventSink(events)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult result = agent.run(
                    task,
                    context(task, "budget-session"),
                    AgentConfig.builder().maxTotalTokens(5).build());

            assertEquals(AgentRunStatus.FAILED, result.status());
            assertTrue(result.failure().contains("Token budget exceeded"));
            assertTrue(events.events().stream()
                    .anyMatch(event -> event.type() == AgentEventType.BUDGET_EXCEEDED));
        }
    }

    @Test
    void enforcesModelDeadlineWithoutRetry() throws Exception {
        SlowModelClient model = new SlowModelClient();
        AgentTask task = AgentTask.of("timeout-1", "slow");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .build()) {
            AgentResult result = agent.run(
                    task,
                    context(task, "timeout-session"),
                    AgentConfig.builder().timeout(Duration.ofMillis(30)).build());

            assertEquals(AgentRunStatus.FAILED, result.status());
            assertTrue(result.failure().contains("deadline"));
            assertEquals(1, model.calls.get());
        }
    }

    @Test
    void cancelsInFlightModelCall() throws Exception {
        SlowModelClient model = new SlowModelClient();
        AgentTask task = AgentTask.of("cancel-1", "slow");
        AgentContext context = context(task, "cancel-session");
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            Future<AgentResult> future = runner.submit(
                    () -> agent.run(task, context, AgentConfig.builder()
                            .timeout(Duration.ofSeconds(5))
                            .build()));
            assertTrue(model.started.await(1, TimeUnit.SECONDS));
            context.cancel();

            assertEquals(AgentRunStatus.CANCELLED,
                    future.get(2, TimeUnit.SECONDS).status());
        } finally {
            runner.shutdownNow();
        }
    }

    @Test
    void checkpointFailureDoesNotReportSuccess() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient().enqueueText("completed output");
        AgentTask task = AgentTask.of("checkpoint-fail-1", "answer");
        CheckpointStore failingStore = new CheckpointStore() {
            @Override
            public void save(AgentCheckpoint checkpoint) {
                throw new IllegalStateException("checkpoint unavailable");
            }

            @Override
            public Optional<AgentCheckpoint> load(String taskId, String sessionId) {
                return Optional.empty();
            }
        };

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .checkpointStore(failingStore)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult result = agent.run(
                    task, context(task, "checkpoint-session"), AgentConfig.builder().build());

            assertEquals(AgentRunStatus.FAILED, result.status());
            assertEquals(AgentState.FAILED, result.state());
            assertTrue(result.failure().contains("checkpoint unavailable"));
        }
    }

    @Test
    void savesFinalAssistantMessageInCompletedCheckpoint() throws Exception {
        InMemoryCheckpointStore checkpoints = new InMemoryCheckpointStore();
        ScriptedModelClient model = new ScriptedModelClient().enqueueText("final answer");
        AgentTask task = AgentTask.of("checkpoint-message-1", "question");
        AgentContext context = context(task, "checkpoint-message-session");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .checkpointStore(checkpoints)
                .build()) {
            AgentResult result = agent.run(task, context, AgentConfig.builder().build());

            assertEquals(AgentRunStatus.SUCCEEDED, result.status());
            AgentCheckpoint checkpoint = checkpoints.load(task.id(), context.getSessionId()).orElseThrow();
            assertEquals(MessageRole.ASSISTANT,
                    checkpoint.messages().get(checkpoint.messages().size() - 1).role());
            assertEquals("final answer",
                    checkpoint.messages().get(checkpoint.messages().size() - 1).content());
            assertEquals(1, result.iterations());
        }
    }

    @Test
    void doesNotExecuteToolsAfterFinalAllowedIteration() throws Exception {
        CountingEchoTool tool = new CountingEchoTool();
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool);
        ScriptedModelClient model = new ScriptedModelClient().enqueue(ModelResponse.builder()
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-limit")
                        .name("echo")
                        .arguments(Map.of("value", "unsafe"))
                        .build()))
                .build());
        AgentTask task = AgentTask.of("iteration-limit-1", "call a tool");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .toolRegistry(tools)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult result = agent.run(
                    task,
                    context(task, "iteration-limit-session"),
                    AgentConfig.builder().toolGroups(Set.of("test")).maxIterations(1).build());

            assertEquals(AgentRunStatus.FAILED, result.status());
            assertEquals(0, tool.calls.get());
        }
    }

    @Test
    void blocksToolOutsideConfiguredGroups() throws Exception {
        CountingEchoTool tool = new CountingEchoTool();
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool);
        ScriptedModelClient model = new ScriptedModelClient().enqueue(ModelResponse.builder()
                .toolCalls(List.of(ToolCall.builder()
                        .id("unauthorized-1")
                        .name("echo")
                        .arguments(Map.of("value", "blocked"))
                        .build()))
                .build());
        AgentTask task = AgentTask.of("group-auth-1", "try hidden tool");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .toolRegistry(tools)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult result = agent.run(
                    task,
                    context(task, "group-auth-session"),
                    AgentConfig.builder().toolGroups(Set.of("default")).build());

            assertEquals(AgentRunStatus.FAILED, result.status());
            assertEquals(0, tool.calls.get());
        }
    }

    @Test
    void rejectsReusingTaskIdForDifferentInput() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient()
                .enqueueText("first")
                .enqueueText("second");
        AgentTask original = AgentTask.of("same-id", "first input");
        AgentContext context = context(original, "same-session");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            agent.run(original, context, AgentConfig.builder().build());
            AgentTask changed = AgentTask.of("same-id", "different input");

            assertThrows(IllegalArgumentException.class,
                    () -> agent.run(changed, context, AgentConfig.builder().build()));
        }
    }

    @Test
    void blocksToolThatRequiresApprovalWithoutPolicy() throws Exception {
        CountingApprovalTool tool = new CountingApprovalTool();
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool);
        ScriptedModelClient model = new ScriptedModelClient().enqueue(ModelResponse.builder()
                .toolCalls(List.of(ToolCall.builder()
                        .id("approval-1")
                        .name("approval")
                        .arguments(Map.of())
                        .build()))
                .build());
        AgentTask task = AgentTask.of("approval-1", "approval");

        try (RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .toolRegistry(tools)
                .retryPolicy(RetryPolicy.none())
                .build()) {
            AgentResult result = agent.run(
                    task,
                    context(task, "approval-session"),
                    AgentConfig.builder().toolGroups(Set.of("test")).build());
            assertEquals(AgentRunStatus.FAILED, result.status());
            assertEquals(0, tool.calls.get());
        }
    }

    @Test
    void managesModelLifecycleWhenExplicitlyEnabled() throws Exception {
        LifecycleModelClient model = new LifecycleModelClient();
        AgentTask task = AgentTask.of("lifecycle-1", "lifecycle");
        RuntimeAgent agent = RuntimeAgent.builder()
                .modelClient(model)
                .manageModelLifecycle(true)
                .build();

        try (agent) {
            assertEquals(AgentRunStatus.SUCCEEDED,
                    agent.run(task, context(task, "lifecycle-session"),
                            AgentConfig.builder().build()).status());
            assertTrue(model.started);
            assertTrue(agent.modelHealth().orElseThrow().healthy());
        }
        assertTrue(model.stopped);
    }

    private static AgentContext context(AgentTask task, String sessionId) {
        return new AgentContext(task.id(), sessionId, null, Map.of());
    }

    private static class EchoTool implements AgentTool {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.builder()
                    .name("echo")
                    .description("Echoes a supplied value back to the model for deterministic testing.")
                    .parameters(Map.of("type", "object", "properties", Map.of()))
                    .idempotent(true)
                    .build();
        }

        @Override
        public String group() {
            return "test";
        }

        @Override
        public ToolResult execute(ToolCall call, ToolExecutionContext context) {
            calls.incrementAndGet();
            return ToolResult.success("echo:" + call.arguments().get("value"));
        }
    }

    private static final class CountingEchoTool extends EchoTool {
    }

    private static final class StatefulEchoTool extends EchoTool {
        @Override
        public ToolResult execute(ToolCall call, ToolExecutionContext context) {
            super.execute(call, context);
            return ToolResult.success("echo:state", Map.of("progress", 42));
        }
    }

    private static final class CountingApprovalTool extends EchoTool {
        @Override
        public ToolDefinition definition() {
            return ToolDefinition.builder()
                    .name("approval")
                    .description("A tool that requires explicit human approval before execution.")
                    .parameters(Map.of("type", "object"))
                    .requiresApproval(true)
                    .build();
        }
    }

    private static final class FlakyModelClient implements ModelClient {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ModelResponse chat(ModelRequest request, InvocationContext context) {
            context.checkActive();
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary model outage");
            }
            return ModelResponse.builder().content("recovered").build();
        }
    }

    private static final class SlowModelClient implements ModelClient {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public ModelResponse chat(ModelRequest request, InvocationContext context) {
            calls.incrementAndGet();
            started.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return ModelResponse.builder().content("late").build();
        }
    }

    private static final class InterruptedToolModelClient implements ModelClient {
        private final AtomicInteger calls = new AtomicInteger();
        private final List<ModelRequest> requests = new ArrayList<>();

        @Override
        public ModelResponse chat(ModelRequest request, InvocationContext context) {
            context.checkActive();
            requests.add(request);
            return switch (calls.incrementAndGet()) {
                case 1 -> ModelResponse.builder()
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-resume")
                                .name("echo")
                                .arguments(Map.of("value", "resume"))
                                .build()))
                        .build();
                case 2 -> throw new IllegalStateException("interrupted after tool");
                default -> ModelResponse.builder().content("finished after resume").build();
            };
        }
    }

    private static final class LifecycleModelClient implements ModelClient, ProviderLifecycle {
        private boolean started;
        private boolean stopped;

        @Override
        public void start(ProviderConfig config) {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public ModelResponse chat(ModelRequest request, InvocationContext context) {
            return ModelResponse.builder().content("lifecycle complete").build();
        }
    }
}
