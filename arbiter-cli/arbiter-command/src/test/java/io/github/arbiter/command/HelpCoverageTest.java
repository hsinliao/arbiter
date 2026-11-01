package io.github.arbiter.command;

import io.github.arbiter.core.service.QueryResult;
import io.github.arbiter.core.service.SystemStatus;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.StringWriter;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpCoverageTest {
    @Test
    void everyCommandHasAHelpDescription() {
        CommandLine commandLine = CommandTestSupport.commandLine(
                CommandTestSupport.nonInteractive(), new StubSecretInput(false),
                CommandTestSupport.noOpAuth(),
                () -> new SystemStatus("UP", "1", "now"),
                name -> new QueryResult(name, "ok"),
                new StringWriter(), new StringWriter());
        ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);

        assertDescription(bundle, commandLine.getCommandSpec(), "root");
    }

    private static void assertDescription(ResourceBundle bundle,
                                          CommandLine.Model.CommandSpec spec,
                                          String path) {
        assertTrue(bundle.containsKey("help." + path + ".description"),
                "Missing help." + path + ".description");
        for (CommandLine child : spec.subcommands().values()) {
            String childPath = "root".equals(path)
                    ? child.getCommandName()
                    : path + "." + child.getCommandName();
            assertDescription(bundle, child.getCommandSpec(), childPath);
        }
    }
}
