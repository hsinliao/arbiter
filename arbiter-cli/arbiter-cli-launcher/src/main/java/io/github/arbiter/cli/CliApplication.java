package io.github.arbiter.cli;

import io.github.arbiter.command.CliRoot;
import io.github.arbiter.command.CommandErrorHandler;
import io.github.arbiter.command.CommandContext;
import io.github.arbiter.command.CommandModule;
import io.github.arbiter.command.CommandRegistry;
import io.github.arbiter.command.ConsoleInputPrompter;
import io.github.arbiter.command.ConsoleSecretInput;
import io.github.arbiter.command.HelpLocalizer;
import io.github.arbiter.command.RuntimeSignals;
import io.github.arbiter.command.i18n.I18n;
import io.github.arbiter.command.module.auth.AuthCommandModule;
import io.github.arbiter.command.module.crypto.CryptoCommandModule;
import io.github.arbiter.command.module.token.TokenCommandModule;
import io.github.arbiter.command.module.user.UserCommandModule;
import io.github.arbiter.command.module.query.QueryCommandModule;
import io.github.arbiter.command.module.server.ServerCommandModule;
import io.github.arbiter.command.module.status.ServiceStatusCommandModule;
import io.github.arbiter.core.input.InputPrompter;
import io.github.arbiter.core.input.SecretInput;
import io.github.arbiter.command.result.ResultRenderer;
import io.github.arbiter.shell.JLineInputPrompter;
import io.github.arbiter.shell.JLineSecretInput;
import io.github.arbiter.shell.ShellSession;
import io.github.arbiter.shell.ShellTerminal;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class CliApplication {
    record Assembled(CommandLine commandLine, InputPrompter input,
                     SecretInput secret, RuntimeSignals signals,
                     ServiceRegistry services) {
    }

    private CliApplication() {
    }

    static Assembled assemble(Map<String, String> environment, Path workingDirectory,
                              Path configDirectory, Locale startupLocale,
                              String fixedResultLanguage,
                              InputPrompter input, SecretInput secret,
                              java.io.OutputStream stdout, java.io.OutputStream stderr) {
        ResultRenderer renderer = new ResultRenderer(environment::get, Locale.getDefault());
        RuntimeSignals signals = new RuntimeSignals(startupLocale);
        AtomicReference<Consumer<String>> logSink = new AtomicReference<>(ignored -> {
        });
        ServiceRegistry services = new ServiceRegistry(environment, signals, logSink,
                configDirectory);
        ProcessServerControlService serverControlService =
                new ProcessServerControlService(ProcessServerControlService.detectAppHome());

        CommandContext context = new CommandContext(input, renderer, signals,
                environment::get, fixedResultLanguage);
        List<CommandModule> modules = List.of(
                new AuthCommandModule(services::auth, secret),
                new TokenCommandModule(services::token, secret),
                new UserCommandModule(services::user, secret),
                new CryptoCommandModule(),
                new ServiceStatusCommandModule(services::system),
                new QueryCommandModule(services::query),
                new ServerCommandModule(serverControlService));
        CommandLine commandLine = CommandRegistry.build(context, modules);
        String implementationVersion = CLIMain.class.getPackage().getImplementationVersion();
        commandLine.getCommandSpec().version("arbiter "
                + (implementationVersion == null ? "development" : implementationVersion));
        commandLine.setOut(OutputEncoding.writer(stdout));
        commandLine.setErr(OutputEncoding.writer(stderr));
        HelpLocalizer.apply(commandLine, startupLocale);
        logSink.set(message -> {
            commandLine.getErr().println(message);
            commandLine.getErr().flush();
        });
        return new Assembled(commandLine, input, secret, signals, services);
    }

    static int runCli(String[] args, Map<String, String> environment, Path workingDirectory,
                      Locale startupLocale, java.io.OutputStream stdout,
                      java.io.OutputStream stderr) {
        Assembled assembled = assemble(environment, workingDirectory,
                UserPaths.configDirectory(), startupLocale, null,
                new ConsoleInputPrompter(startupLocale),
                new ConsoleSecretInput(startupLocale), stdout, stderr);
        CommandLine commandLine = assembled.commandLine();
        CommandErrorHandler.install(commandLine, true);
        return CommandExecutionPipeline.execute(commandLine,
                CliRoot.from(commandLine.getCommandSpec()), environment,
                workingDirectory, assembled.services(), args);
    }

    static int runShell(BootstrapSettings settings, Locale startupLocale,
                        Map<String, String> environment, Path workingDirectory,
                        java.io.OutputStream stdout, java.io.OutputStream stderr) {
        JLineInputPrompter input = new JLineInputPrompter(startupLocale);
        JLineSecretInput secret = new JLineSecretInput(startupLocale);
        Path configDirectory = UserPaths.configDirectory();
        Assembled assembled = assemble(environment, workingDirectory, configDirectory,
                startupLocale, startupLocale.toLanguageTag(), input, secret, stdout, stderr);
        assembled.services().configure(settings);
        try (ShellTerminal terminal = ShellTerminal.create(assembled.commandLine(),
                configDirectory)) {
            input.attach(terminal.reader());
            secret.attach(terminal.reader());
            return new ShellSession(assembled.commandLine(), terminal, startupLocale).run();
        }
    }
}
