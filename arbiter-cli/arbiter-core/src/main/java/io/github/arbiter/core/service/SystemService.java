package io.github.arbiter.core.service;

import io.github.arbiter.core.exception.DomainException;

public interface SystemService {
    SystemStatus status() throws DomainException;
}
