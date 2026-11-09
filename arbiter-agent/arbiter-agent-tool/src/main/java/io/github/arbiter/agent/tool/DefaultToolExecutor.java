package io.github.arbiter.agent.tool;

import io.github.arbiter.agent.spi.model.ToolCall;
import io.github.arbiter.agent.spi.tool.AgentTool;
import io.github.arbiter.agent.spi.tool.ToolExecutionContext;
import io.github.arbiter.agent.spi.tool.ToolError;
import io.github.arbiter.agent.spi.tool.ToolResult;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultToolExecutor implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(DefaultToolExecutor.class.getName());

    private final ToolRegistry registry;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final ToolExecutionListener listener;

    public DefaultToolExecutor(ToolRegistry registry) {
        this(registry, newExecutor(), true, ToolExecutionListener.NOOP);
    }

    public DefaultToolExecutor(
            ToolRegistry registry,
            ExecutorService executor,
            boolean ownsExecutor,
            ToolExecutionListener listener
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownsExecutor = ownsExecutor;
        this.listener = listener == null ? ToolExecutionListener.NOOP : listener;
    }

    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(context, "context");
        if (call.name() == null || call.name().isBlank()) {
            return ToolResult.failure("Tool call name is required");
        }
        Duration configuredTimeout = context.timeout() == null || context.timeout().isNegative()
                ? Duration.ofSeconds(30)
                : context.timeout();
        var available = context.invocation().remainingTime();
        Duration timeout = available.isEmpty()
                ? Duration.ofNanos(1)
                : available.filter(value -> value.compareTo(configuredTimeout) < 0)
                .orElse(configuredTimeout);
        long started = System.nanoTime();
        long timeoutNanos = toNanosSaturated(timeout);
        Future<ToolResult> future = null;
        ToolResult result;
        try {
            AgentTool tool = registry.require(call.name());
            future = executor.submit(() -> tool.execute(call, context));
            while (true) {
                if (context.isCancellationRequested()) {
                    future.cancel(true);
                    result = ToolResult.failure(
                            "Tool execution cancelled",
                            new ToolError("CANCELLED", "Tool execution cancelled", false, Map.of()));
                    break;
                }
                long remaining = timeoutNanos - (System.nanoTime() - started);
                if (remaining <= 0) {
                    future.cancel(true);
                    String message = "Tool timed out after " + timeout.toMillis() + "ms";
                    result = ToolResult.failure(
                            message,
                            new ToolError("TIMEOUT", message, true, Map.of()));
                    break;
                }
                try {
                    result = future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)),
                            TimeUnit.NANOSECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    // Poll cancellation while the tool is running.
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (future != null) {
                future.cancel(true);
            }
            result = ToolResult.failure(
                    "Tool execution interrupted",
                    new ToolError("INTERRUPTED", "Tool execution interrupted", true, Map.of()));
        } catch (ExecutionException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            result = ToolResult.failure(
                    "Tool failed: " + cause.getMessage(),
                    new ToolError("EXECUTION_FAILED", cause.getMessage(), false, Map.of()));
        } catch (RuntimeException error) {
            result = ToolResult.failure(
                    "Tool failed: " + error.getMessage(),
                    new ToolError("EXECUTION_FAILED", error.getMessage(), false, Map.of()));
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        try {
            listener.onResult(call, result, elapsed);
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Tool execution listener failed", error);
        }
        return result;
    }

    @Override
    public void close() {
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }

    private static ExecutorService newExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "arbiter-tool-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newCachedThreadPool(factory);
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
