package io.github.arbiter.command;

import io.github.arbiter.core.service.ManagedServerStatus;
import io.github.arbiter.core.service.ServerControlService;
import io.github.arbiter.core.service.ServerOperationResult;
import io.github.arbiter.core.service.SystemStatus;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCommandTest {
    @Test
    void startsAndReportsServerFromTheCliCommandTree() {
        RecordingServerControl service = new RecordingServerControl();
        StringWriter out = new StringWriter();
        var commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> null, service, out, new StringWriter());

        int exit = commandLine.execute("server", "start", "--lang", "en");

        assertEquals(0, exit);
        assertEquals(1, service.starts);
        assertTrue(out.toString().contains("Server started"));
        assertTrue(out.toString().contains("4242"), out.toString());
    }

    @Test
    void rejectsPortOption() {
        RecordingServerControl service = new RecordingServerControl();
        var commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> null, service, new StringWriter(), new StringWriter());

        assertNotEquals(0, commandLine.execute("server", "start", "--port", "9090"));
        assertEquals(0, service.starts);
    }

    @Test
    void startsAndRestartsWithDebugFlag() {
        RecordingServerControl service = new RecordingServerControl();
        var commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> null, service, new StringWriter(), new StringWriter());

        assertEquals(0, commandLine.execute("server", "start", "--debug", "--lang", "en"));
        assertEquals("127.0.0.1:5005", service.debugStart);
        assertNull(service.debugRestart);

        assertEquals(0, commandLine.execute("server", "restart",
                "--debug=127.0.0.1:5006", "--lang", "en"));
        assertEquals("127.0.0.1:5006", service.debugRestart);
    }

    @Test
    void exposesStatusStopAndRestartSubcommands() {
        RecordingServerControl service = new RecordingServerControl();
        var commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> null, service, new StringWriter(), new StringWriter());

        assertEquals(0, commandLine.execute("server", "status"));
        assertEquals(0, commandLine.execute("server", "stop"));
        assertEquals(0, commandLine.execute("server", "restart"));

        assertEquals(1, service.statusCalls);
        assertEquals(1, service.stopCalls);
        assertEquals(1, service.restarts);
    }

    private static final class RecordingServerControl implements ServerControlService {
        private int starts;
        private int restarts;
        private String debugStart;
        private String debugRestart;
        private int statusCalls;
        private int stopCalls;

        @Override
        public ServerOperationResult start(String debugAddress) {
            starts++;
            debugStart = debugAddress;
            return new ServerOperationResult("start", 4242, false);
        }

        @Override
        public ServerOperationResult restart(String debugAddress) {
            restarts++;
            debugRestart = debugAddress;
            return new ServerOperationResult("restart", 4243, false);
        }

        @Override
        public ServerOperationResult stop() {
            stopCalls++;
            return new ServerOperationResult("stop", 4242, false);
        }

        @Override
        public ManagedServerStatus status() {
            statusCalls++;
            return new ManagedServerStatus(true, 4242);
        }
    }
}
