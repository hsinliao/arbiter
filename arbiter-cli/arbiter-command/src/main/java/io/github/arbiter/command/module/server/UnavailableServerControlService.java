package io.github.arbiter.command.module.server;

import io.github.arbiter.core.exception.ServerControlException;
import io.github.arbiter.core.service.ManagedServerStatus;
import io.github.arbiter.core.service.ServerControlService;
import io.github.arbiter.core.service.ServerOperationResult;

/**
 * Null object for command trees that do not expose local server lifecycle
 * management (for example isolated command tests).
 */
public final class UnavailableServerControlService implements ServerControlService {
    @Override
    public ServerOperationResult start(String debugAddress) {
        throw unavailable();
    }

    @Override
    public ServerOperationResult restart(String debugAddress) {
        throw unavailable();
    }

    @Override
    public ServerOperationResult stop() {
        throw unavailable();
    }

    @Override
    public ManagedServerStatus status() {
        throw unavailable();
    }

    private ServerControlException unavailable() {
        return new ServerControlException("err.server.control.unavailable");
    }
}
