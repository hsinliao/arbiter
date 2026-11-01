package io.github.arbiter.cli;

import io.github.arbiter.command.CliRoot;
import io.github.arbiter.command.result.CommandResult;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * One place for pre-execution concerns shared by every direct CLI command.
 * Today that is lazy HTTP configuration; future hooks (audit, dry-run,
 * confirmations) can be inserted here without touching individual commands.
 */
final class CommandExecutionPipeline {
    private CommandExecutionPipeline() {
    }

    static int execute(CommandLine commandLine, CliRoot root,
                       Map<String, String> environment, Path workingDirectory,
                       ServiceRegistry services, String[] args) {
        commandLine.setExecutionStrategy(parseResult -> {
            if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()
                    || isServerCommand(lastCommand(parseResult))) {
                // Local lifecycle management does not need the remote HTTP config.
                return new CommandLine.RunLast().execute(parseResult);
            }
            try {
                BootstrapSettings settings = BootstrapSettings.resolve(
                        root.config(), environment, workingDirectory);
                services.configure(settings);
            } catch (IllegalArgumentException | IOException configurationError) {
                renderConfigurationError(commandLine, root, configurationError);
                return 2;
            }
            return new CommandLine.RunLast().execute(parseResult);
        });
        return commandLine.execute(args);
    }

    private static CommandLine.Model.CommandSpec lastCommand(
            CommandLine.ParseResult parseResult) {
        CommandLine.ParseResult current = parseResult;
        while (current.subcommand() != null) {
            current = current.subcommand();
        }
        return current.commandSpec();
    }

    private static boolean isServerCommand(CommandLine.Model.CommandSpec spec) {
        return "server".equals(spec.name())
                || (spec.parent() != null && "server".equals(spec.parent().name()));
    }

    private static void renderConfigurationError(CommandLine commandLine, CliRoot root,
                                                 Exception error) {
        String detail = error.getMessage() == null ? "" : error.getMessage();
        var writer = root.renderOptions().json()
                ? commandLine.getOut() : commandLine.getErr();
        root.renderer().render(CommandResult.failure("err.config", 2, detail),
                root.renderOptions(), writer);
        commandLine.getOut().flush();
        commandLine.getErr().flush();
    }
}
