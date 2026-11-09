package io.github.arbiter.agent.runtime;

import io.github.arbiter.agent.core.AgentException;

public class ModelTimeoutException extends AgentException {

    public ModelTimeoutException(String message) {
        super(message);
    }
}
