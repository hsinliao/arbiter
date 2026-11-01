package io.github.arbiter.core.input;

import io.github.arbiter.core.exception.UserAbortedException;

public interface SecretInput {
    String readSecret(String promptKey) throws UserAbortedException;

    String readFromStdin() throws UserAbortedException;

    boolean isInteractive();
}
