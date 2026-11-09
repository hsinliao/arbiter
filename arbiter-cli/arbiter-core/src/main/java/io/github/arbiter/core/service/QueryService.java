package io.github.arbiter.core.service;

import io.github.arbiter.core.exception.DomainException;

public interface QueryService {
    QueryResult query(String name) throws DomainException;
}
