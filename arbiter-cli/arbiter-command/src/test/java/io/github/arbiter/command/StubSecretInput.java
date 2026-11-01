package io.github.arbiter.command;

import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.input.SecretInput;

import java.util.ArrayDeque;
import java.util.Deque;

public final class StubSecretInput implements SecretInput {
    private final Deque<String> secrets = new ArrayDeque<>();
    private boolean interactive;

    public StubSecretInput(boolean interactive, String... values) {
        this.interactive = interactive;
        for (String value : values) {
            secrets.add(value);
        }
    }

    @Override
    public String readSecret(String promptKey) {
        if (secrets.isEmpty()) {
            throw new UserAbortedException();
        }
        return secrets.removeFirst();
    }

    @Override
    public String readFromStdin() {
        if (secrets.isEmpty()) {
            throw new UserAbortedException();
        }
        return secrets.removeFirst();
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }
}
