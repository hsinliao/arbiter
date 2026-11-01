package io.github.arbiter.core.service;

import io.github.arbiter.core.exception.DomainException;

/**
 * Local server lifecycle. Every server tunable comes from configuration files
 * or the environment, so start and restart take no command-line options.
 */
public interface ServerControlService {
    ServerOperationResult start(String debugAddress) throws DomainException;

    ServerOperationResult restart(String debugAddress) throws DomainException;

    ServerOperationResult stop() throws DomainException;

    ManagedServerStatus status() throws DomainException;
}
