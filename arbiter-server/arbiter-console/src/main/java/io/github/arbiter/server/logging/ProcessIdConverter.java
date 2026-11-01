package io.github.arbiter.server.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback {@code %pid} converter backed by the current JVM process handle. */
public final class ProcessIdConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return Long.toString(ProcessHandle.current().pid());
    }
}
