package io.github.arbiter.server.agent.state;

import java.time.Instant;

public record StoredMessage(String role, String content, Instant createdAt) {
}
