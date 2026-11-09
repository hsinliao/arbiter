package io.github.arbiter.agent.core;

@FunctionalInterface
public interface Workflow<T> {

    T execute(AgentTask task, AgentContext context);
}
