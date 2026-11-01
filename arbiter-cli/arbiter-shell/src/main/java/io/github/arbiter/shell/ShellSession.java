package io.github.arbiter.shell;

import io.github.arbiter.command.CliRoot;
import io.github.arbiter.command.CommandErrorHandler;
import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.core.exception.RemoteException;
import picocli.CommandLine;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class ShellSession {
    private final CommandLine commandLine;
    private final ShellTerminal terminal;
    private final Locale locale;
    private final AtomicReference<Thread> activeCommand = new AtomicReference<>();
    private final CliRoot root;

    public ShellSession(CommandLine commandLine, ShellTerminal terminal, Locale locale) {
        this.commandLine = commandLine;
        this.terminal = terminal;
        this.locale = locale;
        this.root = CliRoot.from(commandLine.getCommandSpec());
        CommandErrorHandler.install(commandLine, false);
    }

    private static final List<String> EXIT_COMMANDS =
            List.of("exit", "quit", ":q", ":quit");

    static boolean isExitCommand(String line) {
        return line != null
                && EXIT_COMMANDS.contains(line.trim().toLowerCase(Locale.ROOT));
    }

    public int run() {
        while (true) {
            String line = terminal.readCommand(locale);
            if (line == null) {
                return 0;
            }
            if (line.isBlank()) {
                Thread.interrupted();
                continue;
            }
            if (isExitCommand(line)) {
                return 0;
            }
            try {
                List<String> words = terminal.words(line);
                if (words.isEmpty()) {
                    continue;
                }
                activeCommand.set(Thread.currentThread());
                var previousHandler = terminal.interruptHandler(signal -> {
                    Thread active = activeCommand.get();
                    if (active != null) {
                        active.interrupt();
                    }
                });
                try {
                    root.resetInvocationState();
                    commandLine.execute(words.toArray(String[]::new));
                } finally {
                    terminal.interruptHandler(previousHandler);
                }
            } catch (DomainException error) {
                CommandErrorHandler.render(commandLine, error);
            } catch (RuntimeException unexpected) {
                CommandErrorHandler.render(commandLine, new RemoteException(null, null));
            } finally {
                activeCommand.set(null);
                Thread.interrupted();
            }
        }
    }
}
