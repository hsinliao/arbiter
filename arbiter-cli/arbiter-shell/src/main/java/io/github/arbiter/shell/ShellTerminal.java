package io.github.arbiter.shell;

import io.github.arbiter.core.exception.UsageException;
import io.github.arbiter.command.i18n.I18n;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * JLine assembly only. Commands and business behavior are supplied by the
 * command module through the shared Picocli tree.
 */
public final class ShellTerminal implements AutoCloseable {
    private final Terminal terminal;
    private final LineReader reader;

    private ShellTerminal(Terminal terminal, LineReader reader) {
        this.terminal = terminal;
        this.reader = reader;
    }

    public static ShellTerminal create(CommandLine commandLine, Path configDirectory) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            Path historyFile = configDirectory.resolve("history");
            LineReaderBuilder builder = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new PicocliJLineCompleter(commandLine.getCommandSpec()));
            try {
                java.nio.file.Files.createDirectories(historyFile.getParent());
                builder.variable(LineReader.HISTORY_FILE, historyFile);
            } catch (IOException | SecurityException ignored) {
                // Shell remains usable when persistent history is unavailable.
            }
            LineReader reader = builder.build();
            return new ShellTerminal(terminal, reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize interactive terminal", exception);
        }
    }

    public LineReader reader() {
        return reader;
    }

    public String readCommand(Locale locale) {
        try {
            return reader.readLine(I18n.get(locale, "prompt.shell.command"));
        } catch (org.jline.reader.UserInterruptException interrupted) {
            return "";
        } catch (org.jline.reader.EndOfFileException endOfFile) {
            return null;
        }
    }

    public List<String> words(String line) {
        try {
            ParsedLine parsedLine = reader.getParser().parse(line, 0);
            return parsedLine.words();
        } catch (org.jline.reader.SyntaxError syntaxError) {
            throw new UsageException("err.usage.invalid", syntaxError.getMessage());
        }
    }

    public Terminal.SignalHandler interruptHandler(Terminal.SignalHandler handler) {
        return terminal.handle(Terminal.Signal.INT, handler);
    }

    @Override
    public void close() {
        try {
            reader.getHistory().save();
        } catch (IOException | RuntimeException ignored) {
            // History persistence is non-critical.
        }
        try {
            terminal.close();
        } catch (IOException ignored) {
            // The process is leaving shell mode.
        }
    }
}
