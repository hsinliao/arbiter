package io.github.arbiter.core.service;

public interface SystemService {
    SystemStatus status() throws io.github.arbiter.core.exception.DomainException;
}
