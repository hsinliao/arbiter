package io.github.arbiter.command;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

/**
 * Mutable execution settings shared with transport logging. Help text and
 * command definitions remain immutable after the command tree is built.
 */
public final class RuntimeSignals {
    private final AtomicBoolean verbose = new AtomicBoolean();
    private volatile Locale locale;

    public RuntimeSignals() {
        this(Locale.ENGLISH);
    }

    public RuntimeSignals(Locale locale) {
        this.locale = locale == null ? Locale.ENGLISH : locale;
    }

    public boolean verbose() {
        return verbose.get();
    }

    public void verbose(boolean value) {
        verbose.set(value);
    }

    public Locale locale() {
        return locale;
    }

    public void locale(Locale locale) {
        this.locale = locale == null ? Locale.ENGLISH : locale;
    }
}
