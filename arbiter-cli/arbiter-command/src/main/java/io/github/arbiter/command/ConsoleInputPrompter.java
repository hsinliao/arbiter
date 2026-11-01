package io.github.arbiter.command;

import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.input.InputPrompter;
import io.github.arbiter.command.i18n.I18n;

import java.io.Console;
import java.util.Locale;

public final class ConsoleInputPrompter implements InputPrompter {
    private final Console console;
    private final Locale locale;

    public ConsoleInputPrompter(Locale locale) {
        this.console = System.console();
        this.locale = locale;
    }

    @Override
    public String readLine(String promptKey, Object... args) {
        if (console == null) {
            throw new UserAbortedException();
        }
        String value = console.readLine(I18n.get(locale, promptKey, args));
        if (value == null) {
            throw new UserAbortedException();
        }
        return value;
    }

    @Override
    public boolean isInteractive() {
        return console != null;
    }
}
