package io.github.arbiter.command;

import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.command.i18n.I18n;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class ConsoleSecretInput implements SecretInput {
    private final Console console;
    private final Locale locale;

    public ConsoleSecretInput(Locale locale) {
        this.console = System.console();
        this.locale = locale;
    }

    @Override
    public String readSecret(String promptKey) {
        if (console == null) {
            throw new UserAbortedException();
        }
        char[] value = console.readPassword(I18n.get(locale, promptKey));
        if (value == null) {
            throw new UserAbortedException();
        }
        String secret = new String(value);
        Arrays.fill(value, '\0');
        return secret;
    }

    @Override
    public String readFromStdin() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String value = reader.readLine();
            if (value == null) {
                throw new UserAbortedException();
            }
            return value;
        } catch (IOException exception) {
            throw new UserAbortedException();
        }
    }

    @Override
    public boolean isInteractive() {
        return console != null;
    }
}
