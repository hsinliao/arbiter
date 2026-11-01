package io.github.arbiter.core.input;

import io.github.arbiter.core.exception.UserAbortedException;

public interface InputPrompter {
    /**
     * Reads a non-sensitive text value. EOF or Ctrl+C must raise
     * UserAbortedException rather than returning an empty value.
     */
    String readLine(String promptKey, Object... args) throws UserAbortedException;

    boolean isInteractive();
}
