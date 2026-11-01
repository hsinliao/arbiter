package io.github.arbiter.client;

import io.github.arbiter.client.security.Redactor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactorTest {
    @Test
    void redactsJsonAndKnownSecrets() {
        String redacted = Redactor.redactSecrets(
                "{\"username\":\"alice\",\"password\":\"hunter2\\\"tail\",\"token\":\"abc\"} abc hunter2",
                List.of("abc", "hunter2"));

        assertFalse(redacted.contains("hunter2"));
        assertFalse(redacted.contains("tail"));
        assertFalse(redacted.matches(".*\"token\":\"abc\".*"));
        assertTrue(redacted.contains("***"));
    }
}
