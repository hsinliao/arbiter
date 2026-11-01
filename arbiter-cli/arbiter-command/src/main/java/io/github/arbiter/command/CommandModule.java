package io.github.arbiter.command;

import picocli.CommandLine;

import java.util.List;

/**
 * One feature's contribution to the CLI command tree. Modules are assembled
 * explicitly by the composition root; the registry validates names and order.
 */
public interface CommandModule {
    String name();

    default int order() {
        return 100;
    }

    default List<String> aliases() {
        return List.of();
    }

    default boolean hidden() {
        return false;
    }

    CommandLine commandLine();

    default CommandLine commandLine(CommandContext context) {
        return commandLine();
    }
}
