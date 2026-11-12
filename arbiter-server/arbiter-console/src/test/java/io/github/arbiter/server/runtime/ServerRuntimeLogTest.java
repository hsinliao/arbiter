package io.github.arbiter.server.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRuntimeLogTest {

    @Test
    void writesRuntimeEnvironmentOnSeparateLines(@TempDir Path home) {
        Logger logger = (Logger) LoggerFactory.getLogger(ServerRuntime.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ServerRuntime.logRuntimeEnvironment(home);
        } finally {
            logger.detachAppender(appender);
        }

        assertEquals(7, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().endsWith(" starting"));
        assertTrue(appender.list.get(1).getFormattedMessage().startsWith("java.home="));
        assertTrue(appender.list.get(2).getFormattedMessage().startsWith("java.version="));
        assertTrue(appender.list.get(3).getFormattedMessage().startsWith("os.name="));
        assertTrue(appender.list.get(4).getFormattedMessage().startsWith("os.version="));
        assertTrue(appender.list.get(5).getFormattedMessage().startsWith("os.arch="));
        assertEquals("arbiter.home=" + home,
                appender.list.get(6).getFormattedMessage());
    }

    @Test
    void escapesStructuredLogValues() {
        assertEquals("\"C:\\\\service\\\\jdk25 \\\"preview\\\"\\r\\n\\tnext\"",
                ServerRuntime.logValue("C:\\service\\jdk25 \"preview\"\r\n\tnext"));
        assertEquals("\"<unknown>\"", ServerRuntime.logValue(" "));
    }
}
