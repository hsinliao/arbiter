package io.github.arbiter.agent.core;

public interface Agent {

    AgentResult run(AgentTask task, AgentContext context, AgentConfig config);

    default AgentResult run(AgentTask task) {
        return run(task, AgentContext.create(task.id()), AgentConfig.builder().build());
    }
}
