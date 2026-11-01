package io.github.arbiter.command;

import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.input.InputPrompter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class StubInputPrompter implements InputPrompter {
    private final Deque<String> values = new ArrayDeque<>();
    private final List<String> promptKeys = new ArrayList<>();
    private boolean interactive;

    public StubInputPrompter(boolean interactive, String... presetValues) {
        this.interactive = interactive;
        for (String value : presetValues) {
            values.add(value);
        }
    }

    @Override
    public String readLine(String promptKey, Object... args) {
        promptKeys.add(promptKey);
        if (values.isEmpty()) {
            throw new UserAbortedException();
        }
        return values.removeFirst();
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public List<String> promptKeys() {
        return List.copyOf(promptKeys);
    }
}
