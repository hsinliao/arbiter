package io.github.arbiter.server.config;

import io.github.arbiter.agent.memory.TieredMemory;
import io.github.arbiter.agent.llm.OpenAiCompatibleModelClient;
import io.github.arbiter.agent.runtime.RuntimeAgent;
import io.github.arbiter.agent.tool.ClockTool;
import io.github.arbiter.agent.tool.TextTransformTool;
import io.github.arbiter.agent.tool.ToolRegistry;
import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.server.agent.state.AgentStateRepository;
import io.github.arbiter.server.agent.state.JdbcAgentStateRepository;
import io.github.arbiter.server.agent.tool.QueryResourceTool;
import io.github.arbiter.server.agent.tool.RecentErrorsTool;
import io.github.arbiter.server.agent.tool.RunbookSearchTool;
import io.github.arbiter.server.agent.tool.RuntimeHealthTool;
import io.github.arbiter.server.agent.tool.ServiceStatusTool;
import io.github.arbiter.server.agent.worker.AgentWorker;
import io.github.arbiter.server.auth.FileUserRepository;
import io.github.arbiter.server.service.AgentAssistant;
import io.github.arbiter.server.service.AgentService;
import io.github.arbiter.server.service.QueryService;
import io.github.arbiter.server.service.StatusService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Wires the optional Agent runtime into the Console process. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentProperties.class)
public final class AgentAssistantConfiguration {

    @Bean(name = "agentExecutor", destroyMethod = "shutdown")
    ExecutorService agentExecutor(AgentProperties properties) {
        properties.validate();
        AtomicLong sequence = new AtomicLong();
        return new ThreadPoolExecutor(
                properties.getMaxConcurrentRuns(),
                properties.getMaxConcurrentRuns(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "arbiter-agent-run-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "arbiter.agent", name = "enabled",
            havingValue = "true")
    JdbcAgentStateRepository agentStateRepository(AgentProperties properties)
            throws java.io.IOException {
        properties.validate();
        Path home = ArbiterHome.root();
        if (home == null) {
            throw new IllegalStateException(
                    "arbiter.home is required when the Console assistant is enabled");
        }
        java.nio.file.Files.createDirectories(home.resolve("data"));
        String jdbcUrl = properties.getStorageJdbcUrl().replace(
                "${arbiter.home}",
                home.toAbsolutePath().toString().replace('\\', '/'));
        CryptoKeys.ensure();
        return new JdbcAgentStateRepository(
                jdbcUrl,
                properties.getStorageUsername(),
                properties.getStoragePassword(),
                CryptoKeys.ring());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "arbiter.agent", name = "enabled",
            havingValue = "true")
    RuntimeAgent consoleRuntimeAgent(
            AgentProperties properties,
            JdbcAgentStateRepository stateRepository,
            StatusService statusService,
            QueryService queryService,
            FileUserRepository userRepository
    ) {
        properties.validate();
        Path home = ArbiterHome.root();
        if (home == null) {
            throw new IllegalStateException(
                    "arbiter.home is required when the Console assistant is enabled");
        }

        ToolRegistry tools = new ToolRegistry();
        tools.register(new ClockTool());
        tools.register(new TextTransformTool());
        tools.register(new ServiceStatusTool(statusService));
        tools.register(new RuntimeHealthTool(statusService, userRepository));
        tools.register(new QueryResourceTool(queryService));
        tools.register(new RecentErrorsTool(home.resolve("logs").resolve("console.log")));
        tools.register(new RunbookSearchTool());

        return RuntimeAgent.builder()
                .modelClient(new OpenAiCompatibleModelClient(
                        properties.getBaseUrl(),
                        properties.getApiKey(),
                        properties.getModel(),
                        properties.getTimeout(),
                        properties.getTimeout()))
                .toolRegistry(tools)
                .memory(new TieredMemory())
                .eventSink(stateRepository)
                .manageModelLifecycle(true)
                .build();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "arbiter.agent", name = "enabled",
            havingValue = "true")
    AgentWorker agentWorker(
            JdbcAgentStateRepository stateRepository,
            RuntimeAgent runtimeAgent,
            AgentProperties properties,
            @Qualifier("agentExecutor") ExecutorService executor,
            Clock clock
    ) {
        return new AgentWorker(
                stateRepository, runtimeAgent, properties, executor, clock);
    }

    @Bean
    AgentAssistant agentAssistant(
            AgentProperties properties,
            ObjectProvider<AgentStateRepository> stateRepository,
            Clock clock
    ) {
        return new AgentService(properties, stateRepository.getIfAvailable(), clock);
    }
}
