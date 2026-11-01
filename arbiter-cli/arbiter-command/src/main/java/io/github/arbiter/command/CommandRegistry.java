package io.github.arbiter.command;

import picocli.CommandLine;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles validated feature modules into one Picocli root. The registry, not
 * the feature modules, owns duplicate detection and deterministic help order.
 */
public final class CommandRegistry {
    private CommandRegistry() {
    }

    public static CommandLine build(CommandContext context, List<CommandModule> modules) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(modules, "modules");

        List<CommandModule> sorted = modules.stream()
                .sorted(Comparator.comparingInt(CommandModule::order)
                        .thenComparing(CommandModule::name))
                .toList();
        Set<String> names = new HashSet<>();
        for (CommandModule module : sorted) {
            if (!names.add(module.name())) {
                throw new IllegalArgumentException(
                        "Duplicate command module name: " + module.name());
            }
            for (String alias : module.aliases()) {
                if (!names.add(alias)) {
                    throw new IllegalArgumentException(
                            "Duplicate command name or alias: " + alias);
                }
            }
        }

        CliRoot root = new CliRoot(context.input(), context.renderer(),
                context.signals(), context.environment(),
                context.fixedResultLanguage());
        CommandLine commandLine = new CommandLine(root);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        for (CommandModule module : sorted) {
            CommandLine subcommand = module.commandLine(context);
            if (!module.aliases().isEmpty()) {
                subcommand.getCommandSpec().aliases(
                        module.aliases().toArray(String[]::new));
            }
            subcommand.getCommandSpec().usageMessage().hidden(module.hidden());
            commandLine.addSubcommand(module.name(), subcommand);
        }
        return commandLine;
    }
}
