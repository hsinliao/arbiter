package io.github.arbiter.shell;

import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.command.i18n.I18n;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.Locale;
import java.util.Objects;

public final class JLineSecretInput implements SecretInput {
    private final Locale locale;
    private LineReader reader;

    public JLineSecretInput(Locale locale) {
        this.locale = Objects.requireNonNull(locale);
    }

    public void attach(LineReader reader) {
        this.reader = Objects.requireNonNull(reader);
    }

    @Override
    public String readSecret(String promptKey) {
        if (reader == null) {
            throw new UserAbortedException();
        }
        Object previousDisableHistory = reader.getVariable(LineReader.DISABLE_HISTORY);
        reader.setVariable(LineReader.DISABLE_HISTORY, Boolean.TRUE);
        try {
            String value = reader.readLine(I18n.get(locale, promptKey), '*');
            if (value == null) {
                throw new UserAbortedException();
            }
            return value;
        } catch (UserInterruptException | EndOfFileException aborted) {
            throw new UserAbortedException();
        } finally {
            reader.setVariable(LineReader.DISABLE_HISTORY, previousDisableHistory);
        }
    }

    @Override
    public String readFromStdin() {
        if (reader == null) {
            throw new UserAbortedException();
        }
        Object previousDisableHistory = reader.getVariable(LineReader.DISABLE_HISTORY);
        reader.setVariable(LineReader.DISABLE_HISTORY, Boolean.TRUE);
        try {
            String value = reader.readLine(null, '*');
            if (value == null) {
                throw new UserAbortedException();
            }
            return value;
        } catch (UserInterruptException | EndOfFileException aborted) {
            throw new UserAbortedException();
        } finally {
            reader.setVariable(LineReader.DISABLE_HISTORY, previousDisableHistory);
        }
    }

    @Override
    public boolean isInteractive() {
        return reader != null;
    }
}
