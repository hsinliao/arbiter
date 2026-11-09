package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.core.AgentState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class AgentStateMachine {

    private static final Map<AgentState, Set<AgentState>> TRANSITIONS = transitions();
    private AgentState state;

    public AgentStateMachine() {
        this(AgentState.CREATED);
    }

    public AgentStateMachine(AgentState state) {
        this.state = state == null ? AgentState.CREATED : state;
    }

    public synchronized AgentState state() {
        return state;
    }

    public synchronized AgentState transition(AgentState next) {
        if (next == null) {
            throw new IllegalArgumentException("Next state is required");
        }
        if (state == next) {
            return state;
        }
        if (!TRANSITIONS.getOrDefault(state, Set.of()).contains(next)) {
            throw new IllegalStateException("Illegal agent state transition: " + state + " -> " + next);
        }
        state = next;
        return state;
    }

    public synchronized boolean canTransitionTo(AgentState next) {
        return state == next || TRANSITIONS.getOrDefault(state, Set.of()).contains(next);
    }

    public synchronized boolean isTerminal() {
        return state == AgentState.COMPLETED || state == AgentState.CANCELLED;
    }

    private static Map<AgentState, Set<AgentState>> transitions() {
        Map<AgentState, Set<AgentState>> transitions = new EnumMap<>(AgentState.class);
        transitions.put(AgentState.CREATED, EnumSet.of(AgentState.RUNNING));
        transitions.put(AgentState.RUNNING, EnumSet.of(
                AgentState.WAITING_FOR_MODEL, AgentState.EXECUTING_TOOL,
                AgentState.COMPLETED, AgentState.FAILED, AgentState.CANCELLED));
        transitions.put(AgentState.WAITING_FOR_MODEL, EnumSet.of(
                AgentState.RUNNING, AgentState.EXECUTING_TOOL, AgentState.COMPLETED,
                AgentState.FAILED, AgentState.CANCELLED));
        transitions.put(AgentState.EXECUTING_TOOL, EnumSet.of(
                AgentState.RUNNING, AgentState.WAITING_FOR_MODEL, AgentState.COMPLETED,
                AgentState.FAILED, AgentState.CANCELLED));
        transitions.put(AgentState.FAILED, EnumSet.of(AgentState.RUNNING));
        transitions.put(AgentState.COMPLETED, EnumSet.noneOf(AgentState.class));
        transitions.put(AgentState.CANCELLED, EnumSet.noneOf(AgentState.class));
        return transitions;
    }
}
