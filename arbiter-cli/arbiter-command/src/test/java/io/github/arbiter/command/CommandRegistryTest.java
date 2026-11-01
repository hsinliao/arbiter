package io.github.arbiter.command;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandRegistryTest {
    @Test
    void rejectsDuplicateTopLevelCommandNames() {
        CommandModule module = new CommandModule() {
            @Override
            public String name() {
                return "duplicate";
            }

            @Override
            public CommandLine commandLine() {
                return new CommandLine(new Object());
            }
        };
        CommandContext context = new CommandContext(null, null,
                new RuntimeSignals(), name -> null, null);

        assertThrows(IllegalArgumentException.class,
                () -> CommandRegistry.build(context, List.of(module, module)));
    }
}
