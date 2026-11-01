package io.github.arbiter.command;

import io.github.arbiter.core.exception.UsageException;
import io.github.arbiter.core.exception.UserAbortedException;
import io.github.arbiter.core.input.SecretInput;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Centralizes non-interactive guards, required-value prompting, validation
 * retries, and secret input. Commands should not reimplement these rules.
 */
public final class InteractionPolicy {
    public static final int RETRY_LIMIT = 3;

    private final CliRoot root;
    private final PrintWriter err;

    public InteractionPolicy(CliRoot root, PrintWriter err) {
        this.root = root;
        this.err = err;
    }

    public boolean canPrompt() {
        return !root.noInput() && root.inputPrompter().isInteractive();
    }

    public String requiredText(String supplied, String promptKey,
                               String fieldLabelKey, Pattern pattern,
                               String invalidKey) {
        if (supplied != null && !supplied.isBlank()) {
            validate(pattern, supplied, invalidKey);
            return supplied;
        }
        if (!canPrompt()) {
            throw new UsageException("err.usage.missing",
                    root.renderer().translate(fieldLabelKey, locale()));
        }
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            String value = root.inputPrompter().readLine(promptKey);
            if (value == null) {
                throw new UserAbortedException();
            }
            if (pattern == null || pattern.matcher(value).matches()) {
                return value;
            }
            err.println(root.renderer().translate(invalidKey, locale()));
        }
        throw new UsageException("err.usage.retryExceeded",
                root.renderer().translate(fieldLabelKey, locale()));
    }

    public String readSecret(SecretInput secretInput, boolean fromStdin, String promptKey) {
        String value;
        if (fromStdin) {
            value = secretInput.readFromStdin();
        } else if (root.noInput() || !secretInput.isInteractive()) {
            throw new UsageException("err.usage.password-interactive");
        } else {
            value = secretInput.readSecret(promptKey);
        }
        if (value == null || value.isEmpty()) {
            throw new UsageException("err.usage.password-empty");
        }
        return value;
    }

    private void validate(Pattern pattern, String value, String invalidKey) {
        if (pattern != null && !pattern.matcher(value).matches()) {
            throw new UsageException(invalidKey, value);
        }
    }

    private Locale locale() {
        return root.renderer().resolveLocale(root.renderOptions());
    }
}
