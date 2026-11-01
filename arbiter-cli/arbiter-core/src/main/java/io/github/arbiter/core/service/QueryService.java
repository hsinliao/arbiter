package io.github.arbiter.core.service;

public interface QueryService {
    QueryResult query(String name) throws io.github.arbiter.core.exception.DomainException;
}
