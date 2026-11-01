package io.github.arbiter.command;

import io.github.arbiter.core.service.SystemStatus;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRootTest {
    @Test
    void resetInvocationStateClearsVerboseBetweenShellCommands() {
        CommandLine commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(),
                () -> new SystemStatus("UP", "1", "now"),
                name -> null, new StringWriter(), new StringWriter());

        assertEquals(0, commandLine.execute("status", "--verbose", "--lang", "en"));
        CliRoot root = CliRoot.from(commandLine.getCommandSpec());
        assertTrue(root.verbose());

        root.resetInvocationState();

        assertFalse(root.verbose());
    }
}
