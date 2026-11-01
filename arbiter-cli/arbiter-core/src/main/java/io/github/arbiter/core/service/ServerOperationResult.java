package io.github.arbiter.core.service;

public record ServerOperationResult(String action, long pid, boolean alreadyRunning) {
}
