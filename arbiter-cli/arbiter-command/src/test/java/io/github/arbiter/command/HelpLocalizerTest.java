package io.github.arbiter.command;

import io.github.arbiter.core.service.SystemStatus;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpLocalizerTest {
    @Test
    void appliesHelpDescriptionsWithoutRebuildingCommandLine() {
        StringWriter out = new StringWriter();
        var commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), CommandTestSupport.noSecret(),
                CommandTestSupport.noOpAuth(), () -> new SystemStatus("UP", "1", "now"),
                name -> null, out, new StringWriter());

        assertDoesNotThrow(() -> HelpLocalizer.apply(commandLine, Locale.ENGLISH));
        commandLine.usage(commandLine.getOut());

        assertTrue(out.toString().contains("Explicit configuration file path"), out.toString());
    }
}
